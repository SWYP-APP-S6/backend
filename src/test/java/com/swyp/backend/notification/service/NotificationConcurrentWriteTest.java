package com.swyp.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class NotificationConcurrentWriteTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	NotificationPusher notificationPusher;

	@Autowired
	NotificationService notificationService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private User consumer;
	private Notification notification;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "망원동 주민", null, false, Instant.now()));
		notification = notificationRepository.saveAndFlush(new Notification(
			consumer, NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요", "본문", null));
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void recordingThePushKeepsAReadThatLandedFirst() {
		transactionTemplate.execute(status -> {
			notificationRepository.findById(notification.getId());
			jdbcTemplate.update(
				"update notifications set read_at = ? where id = ?",
				Timestamp.from(Instant.now()), notification.getId());

			notificationPusher.markDelivered(notification.getId());
			return null;
		});

		Notification settled = notificationRepository.findById(notification.getId()).orElseThrow();
		assertThat(settled.getReadAt())
			.as("the batch means to touch the push columns only -- writing the whole row back puts "
					+ "a notification the user already opened back in the unread badge")
			.isNotNull();
		assertThat(settled.getPushState()).isEqualTo(NotificationPushState.SENT);
	}

	@Test
	void readingANotificationDoesNotHandItBackToTheBatch() {
		transactionTemplate.execute(status -> {
			notificationRepository.findById(notification.getId());
			jdbcTemplate.update(
				"update notifications set push_state = 'SENT', pushed_at = ? where id = ?",
				Timestamp.from(Instant.now()), notification.getId());

			notificationService.read(consumer.getId(), notification.getId());
			return null;
		});

		Notification settled = notificationRepository.findById(notification.getId()).orElseThrow();
		assertThat(settled.getPushState())
			.as("the read request loaded the row while it was still PENDING -- writing that value "
					+ "back would put it in front of the batch again and push it twice")
			.isEqualTo(NotificationPushState.SENT);
		assertThat(settled.getReadAt()).isNotNull();
	}

	@Test
	void readingEverythingDoesNotHandTheWholeInboxBackToTheBatch() {
		Notification second = notificationRepository.saveAndFlush(new Notification(
			consumer, NotificationType.PICKUP_COMPLETED, "수령이 완료됐어요", "본문", null));

		transactionTemplate.execute(status -> {
			notificationRepository.findByUserIdAndReadAtIsNull(consumer.getId());
			jdbcTemplate.update("update notifications set push_state = 'SENT' where user_id = ?",
				consumer.getId());

			notificationService.readAll(consumer.getId());
			return null;
		});

		assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getPushState())
			.isEqualTo(NotificationPushState.SENT);
		assertThat(notificationRepository.findById(second.getId()).orElseThrow().getPushState())
			.isEqualTo(NotificationPushState.SENT);
	}
}
