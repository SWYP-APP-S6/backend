package com.swyp.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.notification.NotificationPushProperties;
import com.swyp.backend.notification.entity.DevicePlatform;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.notification.repository.UserDeviceTokenRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
		NotificationPushServiceTest.RecordingPushSenderConfiguration.class})
class NotificationPushServiceTest {

	private static final String PHONE = "fcm-token-of-the-galaxy-s24";
	private static final String TABLET = "fcm-token-of-the-tab-s9";

	@TestConfiguration
	static class RecordingPushSenderConfiguration {

		@Bean
		@Primary
		RecordingPushSender recordingPushSender() {
			return new RecordingPushSender();
		}
	}

	static class RecordingPushSender implements PushSender {

		private final List<String> sentTo = new ArrayList<>();
		private final List<PushMessage> messages = new ArrayList<>();
		private final Map<String, Result> resultByToken = new HashMap<>();
		private Result defaultResult = Result.DELIVERED;

		private String explodingTitle;

		@Override
		public Result send(String fcmToken, PushMessage message) {
			if (message.title().equals(explodingTitle)) {
				throw new IllegalStateException("boom");
			}
			sentTo.add(fcmToken);
			messages.add(message);
			return resultByToken.getOrDefault(fcmToken, defaultResult);
		}

		void throwFor(String title) {
			this.explodingTitle = title;
		}

		void reset() {
			sentTo.clear();
			messages.clear();
			resultByToken.clear();
			defaultResult = Result.DELIVERED;
			explodingTitle = null;
		}

		void answerWith(Result result) {
			this.defaultResult = result;
		}

		void answerWith(String fcmToken, Result result) {
			this.resultByToken.put(fcmToken, result);
		}
	}

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	NotificationPushService notificationPushService;

	@Autowired
	RecordingPushSender pushSender;

	@Autowired
	NotificationPushProperties pushProperties;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserDeviceTokenRepository userDeviceTokenRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private User consumer;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		pushSender.reset();
		consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "망원동 주민", null, false, Instant.now()));
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void aPendingNotificationReachesEveryDeviceOfThatUser() {
		registerDevice(PHONE);
		registerDevice(TABLET);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		assertThat(notificationPushService.dispatchPendingPushes()).isEqualTo(1);

		assertThat(pushSender.sentTo).containsExactlyInAnyOrder(PHONE, TABLET);
		assertThat(pushSender.messages.getFirst().title()).isEqualTo("찜 시간이 끝났어요");
		assertThat(pushSender.messages.getFirst().data())
			.containsEntry("type", "HOLD_EXPIRED")
			.containsEntry("notificationId", String.valueOf(notification.getId()));
		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SENT);
	}

	@Test
	void aDeepLinkRidesAlongOnlyWhenTheNotificationCarriesOne() {
		registerDevice(PHONE);
		notifyWithDeepLink("swyp://holds/7");

		notificationPushService.dispatchPendingPushes();

		assertThat(pushSender.messages.getFirst().data()).containsEntry("deepLink", "swyp://holds/7");
	}

	@Test
	void aNotificationWithoutADeepLinkSendsNoEmptyKey() {
		registerDevice(PHONE);
		notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();

		assertThat(pushSender.messages.getFirst().data()).doesNotContainKey("deepLink");
	}

	@Test
	void aUserWithNoRegisteredDeviceIsSkippedWithoutCallingFcm() {
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		assertThat(notificationPushService.dispatchPendingPushes()).isZero();

		assertThat(pushSender.sentTo).isEmpty();
		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SKIPPED);
	}

	@Test
	void aNotificationOlderThanTheDeadlineIsNeverPushed() {
		registerDevice(PHONE);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");
		backdate(notification, pushProperties.staleAfter().plus(Duration.ofMinutes(1)));

		assertThat(notificationPushService.dispatchPendingPushes()).isZero();

		assertThat(pushSender.sentTo).isEmpty();
		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SKIPPED);
	}

	@Test
	void aDeadTokenIsDroppedSoLaterPushesDoNotRetryIt() {
		registerDevice(PHONE);
		registerDevice(TABLET);
		pushSender.answerWith(TABLET, PushSender.Result.TOKEN_GONE);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();

		assertThat(userDeviceTokenRepository.findByFcmToken(TABLET)).isEmpty();
		assertThat(userDeviceTokenRepository.findByFcmToken(PHONE)).isPresent();
		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SENT);
	}

	@Test
	void whenEveryDeviceIsGoneThereIsNothingLeftToDeliverTo() {
		registerDevice(PHONE);
		pushSender.answerWith(PushSender.Result.TOKEN_GONE);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();

		assertThat(userDeviceTokenRepository.findByFcmToken(PHONE)).isEmpty();
		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SKIPPED);
	}

	@Test
	void anOutageKeepsThePushPendingUntilTheAttemptsRunOut() {
		registerDevice(PHONE);
		pushSender.answerWith(PushSender.Result.RETRYABLE);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		for (int attempt = 1; attempt < pushProperties.maxAttempts(); attempt++) {
			notificationPushService.dispatchPendingPushes();
			assertThat(stateOf(notification)).isEqualTo(NotificationPushState.PENDING);
		}
		notificationPushService.dispatchPendingPushes();

		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.FAILED);
		assertThat(pushSender.sentTo).hasSize(pushProperties.maxAttempts());
	}

	@Test
	void aPushFcmRefusesIsRecordedAsFailedRatherThanQuietlySkipped() {
		registerDevice(PHONE);
		pushSender.answerWith(PushSender.Result.REJECTED);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();

		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.FAILED);
		assertThat(userDeviceTokenRepository.findByFcmToken(PHONE)).isPresent();
	}

	@Test
	void withoutFcmConfiguredTheNotificationStaysInTheInboxOnly() {
		registerDevice(PHONE);
		pushSender.answerWith(PushSender.Result.DISABLED);
		Notification notification = notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();

		assertThat(stateOf(notification)).isEqualTo(NotificationPushState.SKIPPED);
		assertThat(userDeviceTokenRepository.findByFcmToken(PHONE)).isPresent();
	}

	@Test
	void oneNotificationBlowingUpDoesNotStallTheOnesBehindIt() {
		registerDevice(PHONE);
		Notification exploding = notify(NotificationType.HOLD_EXPIRED, "터지는 알림");
		Notification following = notify(NotificationType.PICKUP_COMPLETED, "뒤에 선 알림");
		pushSender.throwFor("터지는 알림");

		notificationPushService.dispatchPendingPushes();

		assertThat(stateOf(following))
			.as("a batch that dies on one row keeps picking the same row forever and nothing "
					+ "behind it is ever delivered")
			.isEqualTo(NotificationPushState.SENT);
		assertThat(stateOf(exploding)).isEqualTo(NotificationPushState.PENDING);
		assertThat(notificationRepository.findById(exploding.getId()).orElseThrow()
			.getPushAttempts()).isEqualTo(1);
	}

	@Test
	void aNotificationIsPushedOnceEvenIfTheBatchRunsAgain() {
		registerDevice(PHONE);
		notify(NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");

		notificationPushService.dispatchPendingPushes();
		assertThat(notificationPushService.dispatchPendingPushes()).isZero();

		assertThat(pushSender.sentTo).hasSize(1);
	}

	private NotificationPushState stateOf(Notification notification) {
		return notificationRepository.findById(notification.getId()).orElseThrow().getPushState();
	}

	private void backdate(Notification notification, Duration age) {
		jdbcTemplate.update("update notifications set created_at = ? where id = ?",
			Timestamp.from(Instant.now().minus(age)), notification.getId());
	}

	private void registerDevice(String fcmToken) {
		userDeviceTokenRepository.saveAndFlush(
			new UserDeviceToken(consumer, DevicePlatform.ANDROID, fcmToken, Instant.now()));
	}

	private Notification notify(NotificationType type, String title) {
		return notificationRepository.saveAndFlush(
			new Notification(consumer, type, title, title + " 알림 본문", null));
	}

	private Notification notifyWithDeepLink(String deepLink) {
		return notificationRepository.saveAndFlush(new Notification(
			consumer, NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요", "본문", deepLink));
	}
}
