package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
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
class HoldExpiryServiceTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	HoldExpiryService holdExpiryService;

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

	private Product product;

	@BeforeEach
	void setUp() {
		clearCommittedRows();

		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store store = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);

		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		product = productRepository.saveAndFlush(new Product(
				store, "복숭아 4입", ProductCategory.FRUIT, 5, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/peach.jpg"));
	}

	private Hold hold(String nickname, int qty, Instant expiresAt) {
		User user = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, nickname, null, false, Instant.now()));
		product.hold(qty);
		productRepository.saveAndFlush(product);
		return holdRepository.saveAndFlush(HoldFixture.hold(user, product, qty, expiresAt));
	}

	@AfterEach
	void tearDown() {
		clearCommittedRows();
	}

	private void clearCommittedRows() {
		appDataCleaner.clear();
	}

	private Product reloaded() {
		return productRepository.findById(product.getId()).orElseThrow();
	}

	@Test
	void everyItemInAnOverdueGroupComesBackAndTheOwnerIsTold() {
		Product onion = productRepository.saveAndFlush(new Product(
				product.getStore(), "양파 1.5kg", ProductCategory.VEGETABLE, 5, 6_000, 3_000,
				product.getPickupStartAt(), product.getPickupEndAt(),
				"https://cdn.example.com/onion.jpg"));
		Hold hold = hold("두가지담은소비자", 2, Instant.now().minusSeconds(60));
		onion.hold(3);
		productRepository.saveAndFlush(onion);
		hold.addItem(onion, 3);
		holdRepository.saveAndFlush(hold);
		long notificationsBefore = notificationRepository.count();

		assertThat(holdExpiryService.expireOverdueHolds()).isEqualTo(1);

		assertThat(reloaded().getAvailableQty()).isEqualTo(5);
		assertThat(productRepository.findById(onion.getId()).orElseThrow().getAvailableQty())
			.isEqualTo(5);
		assertThat(notificationRepository.count())
			.as("both sides learn: the consumer that the time ran out, the owner that they may "
					+ "have handed the goods over already and simply not tapped yet")
			.isEqualTo(notificationsBefore + 2);
	}

	@Test
	void anExpiredHoldTellsTheConsumerAndTheOwnerSeparately() {
		Hold overdue = hold("만료될소비자", 2, Instant.now().minusSeconds(60));
		Long consumerId = overdue.getUser().getId();
		Long ownerId = product.getStore().getOwner().getId();

		holdExpiryService.expireOverdueHolds();

		assertThat(notificationRepository.findByUserIdAndReadAtIsNull(consumerId))
			.singleElement()
			.satisfies(notification -> assertThat(notification.getType())
					.isEqualTo(com.swyp.backend.notification.entity.NotificationType.HOLD_EXPIRED));
		assertThat(notificationRepository.findByUserIdAndReadAtIsNull(ownerId))
			.singleElement()
			.satisfies(notification -> assertThat(notification.getType())
					.isEqualTo(com.swyp.backend.notification.entity.NotificationType.HOLD_UNCONFIRMED));
	}

	@Test
	void anOverdueHoldGivesItsQuantityBack() {
		Hold overdue = hold("만료될소비자", 2, Instant.now().minusSeconds(60));
		assertThat(reloaded().getAvailableQty()).isEqualTo(3);

		assertThat(holdExpiryService.expireOverdueHolds()).isEqualTo(1);

		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.EXPIRED);
		assertThat(reloaded().getAvailableQty()).isEqualTo(5);
		assertThat(reloaded().getHeldQty()).isZero();
	}

	@Test
	void severalOverdueHoldsOnOneProductAllAddBack() {
		hold("소비자1", 1, Instant.now().minusSeconds(60));
		hold("소비자2", 2, Instant.now().minusSeconds(30));
		assertThat(reloaded().getAvailableQty()).isEqualTo(2);

		assertThat(holdExpiryService.expireOverdueHolds()).isEqualTo(2);

		assertThat(reloaded().getAvailableQty())
			.as("each release reads and writes the same product row -- a lost update here "
					+ "silently swallows one hold's quantity")
			.isEqualTo(5);
		assertThat(reloaded().getHeldQty()).isZero();
	}

	@Test
	void aHoldThatHasNotExpiredIsLeftAlone() {
		Hold live = hold("아직인소비자", 1, Instant.now().plusSeconds(600));

		assertThat(holdExpiryService.expireOverdueHolds()).isZero();

		assertThat(holdRepository.findById(live.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(reloaded().getAvailableQty()).isEqualTo(4);
	}

	@Test
	void runningTheBatchTwiceDoesNotGiveTheQuantityBackTwice() {
		hold("만료될소비자", 2, Instant.now().minusSeconds(60));

		assertThat(holdExpiryService.expireOverdueHolds()).isEqualTo(1);
		assertThat(holdExpiryService.expireOverdueHolds())
			.as("the second pass must find nothing -- releasing twice invents stock")
			.isZero();

		assertThat(reloaded().getAvailableQty()).isEqualTo(5);
		assertThat(reloaded().getHeldQty()).isZero();
	}

	@Test
	void aHoldCanceledBetweenTheScanAndTheLockIsSkippedRatherThanThrowing() {
		Hold overdue = hold("취소한소비자", 2, Instant.now().minusSeconds(60));
		Hold canceled = holdRepository.findById(overdue.getId()).orElseThrow();
		canceled.cancelByUser(Instant.now());
		holdRepository.saveAndFlush(canceled);
		Product released = reloaded();
		released.releaseHold(2);
		productRepository.saveAndFlush(released);

		assertThat(holdExpiryService.expireOverdueHolds())
			.as("Hold.expire() throws on a non-HOLDING row, and one throw would roll the whole "
					+ "batch back -- the status re-check has to skip instead")
			.isZero();

		assertThat(reloaded().getAvailableQty()).isEqualTo(5);
		assertThat(reloaded().getHeldQty()).isZero();
	}

	@Test
	void overdueHoldsOnDifferentProductsAllExpireInOnePass() {
		hold("소비자1", 2, Instant.now().minusSeconds(60));

		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(5);
		Product second = productRepository.saveAndFlush(new Product(
				product.getStore(), "대파 1단", ProductCategory.VEGETABLE, 4, 5_000, 3_500,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/leek.jpg"));
		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자2", null, false, Instant.now()));
		second.hold(3);
		productRepository.saveAndFlush(second);
		holdRepository.saveAndFlush(HoldFixture.hold(other, second, 3, Instant.now().minusSeconds(30)));

		assertThat(holdExpiryService.expireOverdueHolds())
			.as("the scan groups by product and loops -- stopping after the first group would "
					+ "leave every other store's holds pinned forever")
			.isEqualTo(2);

		assertThat(reloaded().getAvailableQty()).isEqualTo(5);
		assertThat(productRepository.findById(second.getId()).orElseThrow().getAvailableQty())
			.isEqualTo(4);
		assertThat(productRepository.findById(second.getId()).orElseThrow().getHeldQty()).isZero();
	}

	@Test
	void theScheduledPassOpensNoTransactionOfItsOwnAndExpiresOneHoldPerTransaction()
			throws Exception {
		java.lang.reflect.Method scheduled = HoldExpiryService.class.getMethod("expireOverdueHolds");
		assertThat(scheduled.isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class))
			.isTrue();

		assertThat(java.util.Arrays.stream(HoldExpiryService.class.getDeclaredMethods())
				.noneMatch(method -> method.isAnnotationPresent(
						org.springframework.transaction.annotation.Transactional.class)))
			.as("a @Transactional method on this bean would be reachable by self-invocation, which "
					+ "bypasses the proxy and runs the batch with no transaction at all")
			.isTrue();
		assertThat(HoldExpirer.class
				.getMethod("expire", Long.class, java.util.List.class)
				.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
			.as("one transaction per hold keeps the locks a pass holds to a single hold's products, "
					+ "so two passes cannot take them in opposite orders")
			.isTrue();
	}

	@Test
	void expiringEverythingPutsTheProductBackOnSale() {
		hold("전량소비자", 5, Instant.now().minusSeconds(60));
		assertThat(reloaded().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);

		holdExpiryService.expireOverdueHolds();

		assertThat(reloaded().getStatus()).isEqualTo(ProductStatus.ON_SALE);
	}
}
