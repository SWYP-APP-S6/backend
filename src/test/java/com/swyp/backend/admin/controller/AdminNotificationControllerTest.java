package com.swyp.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.notification.entity.DevicePlatform;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.notification.repository.UserDeviceTokenRepository;
import com.swyp.backend.notification.service.PushSender;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class,
		AdminNotificationControllerTest.StubPushSenderConfiguration.class})
class AdminNotificationControllerTest {

	private static final String EMAIL = "push-admin@swyp.test";
	private static final String PASSWORD = "push-admin-1234";

	@TestConfiguration
	static class StubPushSenderConfiguration {
		@Bean
		@Primary
		StubPushSender stubPushSender() {
			return new StubPushSender();
		}
	}

	static class StubPushSender implements PushSender {
		final List<String> sentTo = new ArrayList<>();

		@Override
		public Result send(String fcmToken, PushMessage message) {
			sentTo.add(fcmToken);
			return Result.DELIVERED;
		}
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserDeviceTokenRepository deviceTokenRepository;

	@Autowired
	StubPushSender pushSender;

	@Autowired
	PasswordEncoder passwordEncoder;

	private User consumer;
	private Notification failed;
	private Notification pending;
	private Notification sent;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		pushSender.sentTo.clear();
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
					new Admin(EMAIL, "Push Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		deviceTokenRepository.saveAndFlush(
				new UserDeviceToken(consumer, DevicePlatform.ANDROID, "fcm-token-phone", Instant.now()));

		failed = notification(consumer, NotificationType.HOLD_EXPIRED, "찜 시간이 끝났어요");
		failed.failPush();
		notificationRepository.saveAndFlush(failed);
		pending = notification(consumer, NotificationType.PICKUP_COMPLETED, "수령이 완료됐어요");
		sent = notification(owner, NotificationType.NEW_HOLD_RECEIVED, "새 찜이 들어왔어요");
		sent.markPushDelivered(Instant.now());
		notificationRepository.saveAndFlush(sent);
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Notification notification(User user, NotificationType type, String title) {
		return notificationRepository.saveAndFlush(
				new Notification(user, type, title, title + " 본문", "mangro://holds/1"));
	}

	private String accessToken() throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	@Test
	void notifications_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/notifications")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/notifications/push-summary")).andExpect(status().isUnauthorized());
	}

	@Test
	void theSummaryCountsEachPushState_andSaysWhetherPushIsOn() throws Exception {
		mockMvc.perform(get("/admin/notifications/push-summary")
						.header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pushEnabled").value(true))
				.andExpect(jsonPath("$.data.allTime.pending").value(1))
				.andExpect(jsonPath("$.data.allTime.sent").value(1))
				.andExpect(jsonPath("$.data.allTime.failed").value(1))
				.andExpect(jsonPath("$.data.allTime.skipped").value(0))
				.andExpect(jsonPath("$.data.last24Hours.failed").value(1))
				.andExpect(jsonPath("$.data.oldestPendingAt").isNotEmpty());
	}

	@Test
	void theListComesNewestFirst_andNarrowsByPushStateAndUser() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/notifications").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[0].id").value(sent.getId()))
				.andExpect(jsonPath("$.data.content[0].user.nickname").value("점주"))
				.andExpect(jsonPath("$.data.content[0].pushState").value("SENT"))
				.andExpect(jsonPath("$.data.content[2].pushState").value("FAILED"));

		mockMvc.perform(get("/admin/notifications").param("pushState", "FAILED")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(failed.getId()));

		mockMvc.perform(get("/admin/notifications").param("userId", String.valueOf(consumer.getId()))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void aFailedPushCanBeResent_butAPendingOrSentOneCannot() throws Exception {
		String token = accessToken();

		mockMvc.perform(post("/admin/notifications/{id}/push-retry", failed.getId())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pushState").value("SENT"))
				.andExpect(jsonPath("$.data.pushedAt").isNotEmpty());
		assertThat(pushSender.sentTo).containsExactly("fcm-token-phone");
		assertThat(notificationRepository.findById(failed.getId()).orElseThrow().getPushState())
				.isEqualTo(NotificationPushState.SENT);

		mockMvc.perform(post("/admin/notifications/{id}/push-retry", pending.getId())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PUSH_NOT_RESENDABLE"));
		mockMvc.perform(post("/admin/notifications/{id}/push-retry", sent.getId())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict());
	}
}
