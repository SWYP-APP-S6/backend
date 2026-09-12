package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.HoldFixture;
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
import java.time.LocalTime;
import java.util.ArrayList;
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
class OwnerHoldPickupConcurrencyTest {

	@Autowired
	AppDataCleaner appDataCleaner;

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
	private Store store;

	@BeforeEach
	void setUp() {
		clearAll();

		owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "청과마을사장", null, false, Instant.now()));
		store = storeRepository.saveAndFlush(new Store(
			owner, "청과마을", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
	}

	@AfterEach
	void tearDown() {
		clearAll();
	}

	@Test
	void twoHoldsOnOneProduct_completedAtOnce_bothLeaveHeldQty() throws Exception {
		Product product = createProduct("당근", 10);
		product.hold(1);
		product.hold(1);
		productRepository.saveAndFlush(product);
		Hold first = holding(product, 1);
		Hold second = holding(product, 1);

		List<Throwable> failures = completeAtOnce(first.getId(), second.getId());

		assertThat(failures).isEmpty();
		assertThat(heldQtyOf(product)).isZero();
	}

	@Test
	void oneHold_completedTwiceAtOnce_completesOnce() throws Exception {
		Product product = createProduct("당근", 10);
		product.hold(2);
		productRepository.saveAndFlush(product);
		Hold hold = holding(product, 2);

		List<Throwable> failures = completeAtOnce(hold.getId(), hold.getId());

		assertThat(failures).singleElement()
			.isInstanceOf(BusinessException.class)
			.extracting(failure -> ((BusinessException) failure).getCode())
			.isEqualTo(HoldErrorCode.HOLD_ALREADY_RESOLVED);
		assertThat(heldQtyOf(product)).isZero();
		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.COMPLETED);
		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	private List<Throwable> completeAtOnce(Long... holdIds) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(holdIds.length);
		CountDownLatch ready = new CountDownLatch(holdIds.length);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(holdIds.length);
		List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
		AtomicInteger completed = new AtomicInteger();

		try {
			for (Long holdId : holdIds) {
				pool.submit(() -> {
					try {
						ready.countDown();
						start.await();
						ownerHoldService.completePickup(owner.getId(), holdId);
						completed.incrementAndGet();
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
		assertThat(completed.get() + failures.size()).isEqualTo(holdIds.length);
		return failures;
	}

	private int heldQtyOf(Product product) {
		return productRepository.findById(product.getId()).orElseThrow().getHeldQty();
	}

	private Product createProduct(String name, int initialQty) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.VEGETABLE, initialQty, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private Hold holding(Product product, int qty) {
		User consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "손님", null, false, Instant.now()));
		return holdRepository.saveAndFlush(
			HoldFixture.hold(consumer, product, qty, Instant.now().plus(Duration.ofMinutes(15))));
	}

	private void clearAll() {
		appDataCleaner.clear();
	}
}
