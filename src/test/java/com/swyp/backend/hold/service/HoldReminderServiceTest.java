package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
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
class HoldReminderServiceTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	HoldReminderService holdReminderService;

	@Autowired
	HoldProperties holdProperties;

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
		appDataCleaner.clear();

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

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void aHoldInsideTheLeadTimeWarnsItsOwnerOfTheClock() {
		Hold closing = holding("곧끝날소비자", withinLead());

		assertThat(holdReminderService.remindExpiringHolds()).isEqualTo(1);

		assertThat(notificationRepository.findByUserIdAndReadAtIsNull(closing.getUser().getId()))
			.singleElement()
			.satisfies(notification -> {
				assertThat(notification.getType()).isEqualTo(NotificationType.HOLD_EXPIRING_SOON);
				assertThat(notification.getBody()).contains("청과마을");
			});
	}

	@Test
	void theSameHoldIsWarnedOnlyOnceHoweverOftenTheScanRuns() {
		Hold closing = holding("곧끝날소비자", withinLead());

		assertThat(holdReminderService.remindExpiringHolds()).isEqualTo(1);
		assertThat(holdReminderService.remindExpiringHolds())
			.as("the scan interval is shorter than the lead time, so without a mark the same hold "
					+ "is warned again on every pass")
			.isZero();

		assertThat(notificationRepository.findByUserIdAndReadAtIsNull(closing.getUser().getId()))
			.hasSize(1);
	}

	@Test
	void aHoldWithPlentyOfTimeLeftIsNotWarnedYet() {
		holding("여유있는소비자", Instant.now().plus(holdProperties.expiryReminderLead())
			.plus(Duration.ofMinutes(10)));

		assertThat(holdReminderService.remindExpiringHolds()).isZero();

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	void aHoldThatHasAlreadyRunOutIsLeftToTheExpiryBatch() {
		holding("이미지난소비자", Instant.now().minusSeconds(60));

		assertThat(holdReminderService.remindExpiringHolds())
			.as("HOLD_EXPIRED is that hold's message -- warning it is about to expire would be a lie")
			.isZero();

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	void aHoldThatIsNoLongerHoldingIsNotWarned() {
		Hold canceled = holding("취소한소비자", withinLead());
		canceled.cancelByUser(Instant.now());
		holdRepository.saveAndFlush(canceled);

		assertThat(holdReminderService.remindExpiringHolds()).isZero();

		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	void theMarkIsWrittenSoARestartDoesNotWarnAgain() {
		Hold closing = holding("곧끝날소비자", withinLead());

		holdReminderService.remindExpiringHolds();

		assertThat(holdRepository.findById(closing.getId()).orElseThrow().getExpiryRemindedAt())
			.isNotNull();
	}

	private Instant withinLead() {
		return Instant.now().plus(holdProperties.expiryReminderLead()).minus(Duration.ofMinutes(1));
	}

	private Hold holding(String nickname, Instant expiresAt) {
		User user = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, nickname, null, false, Instant.now()));
		product.hold(1);
		productRepository.saveAndFlush(product);
		return holdRepository.saveAndFlush(HoldFixture.hold(user, product, 1, expiresAt));
	}
}
