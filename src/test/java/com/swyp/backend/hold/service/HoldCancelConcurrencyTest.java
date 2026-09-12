package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.exception.HoldErrorCode;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@TestPropertySource(properties = "hold.expiry-scan-interval=1h")
class HoldCancelConcurrencyTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	HoldService holdService;

	@Autowired
	OwnerHoldService ownerHoldService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	NotificationRepository notificationRepository;

	private User owner;
	private User consumer;
	private Store store;

	@BeforeEach
	void setUp() {
		clearCommittedRows();

		owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "청과마을사장", null, false, Instant.now()));
		consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "손님", null, false, Instant.now()));
		Store unapproved = new Store(
			owner, "청과마을", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
		unapproved.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		unapproved.approve();
		store = storeRepository.saveAndFlush(unapproved);
	}

	@AfterEach
	void tearDown() {
		clearCommittedRows();
	}

	@Test
	void twoTapsOnAddAtOnceLandInOneGroupRatherThanColliding() throws Exception {
		Product carrot = createProduct("당근", 10);
		Product onion = createProduct("양파", 10);

		List<Throwable> failures = runTogether(
			() -> holdService.create(consumer.getId(), new HoldCreateRequest(carrot.getId(), 1)),
			() -> holdService.create(consumer.getId(), new HoldCreateRequest(onion.getId(), 2)));

		assertThat(failures).isEmpty();
		assertThat(holdRepository.count())
			.as("a user has one hold in progress, so the second tap has to join the first")
			.isEqualTo(1);
		Hold hold = holdRepository
			.findDetailById(holdRepository.findAll().getFirst().getId())
			.orElseThrow();
		assertThat(hold.getItems()).hasSize(2);
		assertThat(reload(carrot).getAvailableQty()).isEqualTo(9);
		assertThat(reload(onion).getAvailableQty()).isEqualTo(8);
	}

	@Test
	void theSameHoldCanceledTwiceAtOnceGivesTheQuantityBackOnce() throws Exception {
		Product product = createProduct("당근", 10);
		Hold hold = holding(product, 2);

		List<Throwable> failures = runTogether(
			() -> holdService.cancel(consumer.getId(), hold.getId()),
			() -> holdService.cancel(consumer.getId(), hold.getId()));

		assertThat(failures).singleElement()
			.isInstanceOf(BusinessException.class)
			.extracting(failure -> ((BusinessException) failure).getCode())
			.isEqualTo(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		assertThat(statusOf(hold)).isEqualTo(HoldStatus.CANCELED);
		Product settled = reload(product);
		assertThat(settled.getAvailableQty()).isEqualTo(10);
		assertThat(settled.getHeldQty()).isZero();
	}

	@Test
	void cancelingWhileTheOwnerCompletesThePickupLeavesExactlyOneOutcome() throws Exception {
		Product product = createProduct("당근", 10);
		Hold hold = holding(product, 2);

		List<Throwable> failures = runTogether(
			() -> holdService.cancel(consumer.getId(), hold.getId()),
			() -> ownerHoldService.completePickup(owner.getId(), hold.getId()));

		assertThat(failures).singleElement()
			.isInstanceOf(BusinessException.class)
			.extracting(failure -> ((BusinessException) failure).getCode())
			.isEqualTo(HoldErrorCode.HOLD_ALREADY_RESOLVED);

		Product settled = reload(product);
		assertThat(settled.getHeldQty()).isZero();
		if (statusOf(hold) == HoldStatus.CANCELED) {
			assertThat(settled.getAvailableQty()).isEqualTo(10);
			assertThat(notificationRepository.count()).isZero();
		} else {
			assertThat(statusOf(hold)).isEqualTo(HoldStatus.COMPLETED);
			assertThat(settled.getAvailableQty()).isEqualTo(8);
			assertThat(notificationRepository.count()).isEqualTo(1);
		}
	}

	@Test
	void cancelingTwoHoldsOnOneProductAtOnceGivesBothQuantitiesBack() throws Exception {
		Product product = createProduct("당근", 10);
		User second = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "다른손님", null, false, Instant.now()));
		Hold mine = holding(product, 2);
		Hold theirs = holding(second, product, 3);

		List<Throwable> failures = runTogether(
			() -> holdService.cancel(consumer.getId(), mine.getId()),
			() -> holdService.cancel(second.getId(), theirs.getId()));

		assertThat(failures).isEmpty();
		Product settled = reload(product);
		assertThat(settled.getAvailableQty()).isEqualTo(10);
		assertThat(settled.getHeldQty()).isZero();
	}

	private List<Throwable> runTogether(Runnable... actions) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(actions.length);
		CountDownLatch ready = new CountDownLatch(actions.length);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(actions.length);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger succeeded = new AtomicInteger();

		try {
			for (Runnable action : actions) {
				pool.submit(() -> {
					try {
						ready.countDown();
						start.await();
						action.run();
						succeeded.incrementAndGet();
					} catch (Throwable failure) {
						failures.add(failure);
					} finally {
						done.countDown();
					}
				});
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}
		assertThat(succeeded.get() + failures.size()).isEqualTo(actions.length);
		return failures;
	}

	private HoldStatus statusOf(Hold hold) {
		return holdRepository.findById(hold.getId()).orElseThrow().getStatus();
	}

	private Product reload(Product product) {
		return productRepository.findById(product.getId()).orElseThrow();
	}

	private Product createProduct(String name, int initialQty) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.VEGETABLE, initialQty, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private Hold holding(Product product, int qty) {
		return holding(consumer, product, qty);
	}

	private Hold holding(User user, Product product, int qty) {
		product.hold(qty);
		productRepository.saveAndFlush(product);
		return holdRepository.saveAndFlush(
			HoldFixture.hold(user, product, qty, Instant.now().plus(Duration.ofMinutes(15))));
	}

	private void clearCommittedRows() {
		appDataCleaner.clear();
	}
}
