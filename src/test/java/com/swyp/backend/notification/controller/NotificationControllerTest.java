package com.swyp.backend.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class NotificationControllerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private User consumer;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = newUser(UserRole.CONSUMER, "망원동 주민");
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void theInboxArrivesNewestFirstWithTheUnreadBadge() throws Exception {
		notify(consumer, NotificationType.HOLD_CREATED, "찜했어요");
		Notification newest = notify(consumer, NotificationType.PICKUP_COMPLETED, "수령이 완료됐어요");

		mockMvc.perform(get("/notifications").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.unreadCount").value(2))
			.andExpect(jsonPath("$.data.notifications.totalElements").value(2))
			.andExpect(jsonPath("$.data.notifications.content[0].id").value(newest.getId()))
			.andExpect(jsonPath("$.data.notifications.content[0].type").value("PICKUP_COMPLETED"))
			.andExpect(jsonPath("$.data.notifications.content[0].title").value("수령이 완료됐어요"))
			.andExpect(jsonPath("$.data.notifications.content[0].notifiedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.notifications.content[0].readAt").doesNotExist())
			.andExpect(jsonPath("$.data.notifications.content[1].type").value("HOLD_CREATED"));
	}

	@Test
	void theInboxHoldsOnlyTheCallersOwnNotifications() throws Exception {
		notify(consumer, NotificationType.HOLD_CREATED, "내 알림");
		notify(newUser(UserRole.CONSUMER, "남"), NotificationType.HOLD_CREATED, "남의 알림");

		mockMvc.perform(get("/notifications").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.unreadCount").value(1))
			.andExpect(jsonPath("$.data.notifications.totalElements").value(1))
			.andExpect(jsonPath("$.data.notifications.content[0].title").value("내 알림"));
	}

	@Test
	void readingOneClearsItFromTheBadge() throws Exception {
		Notification notification = notify(consumer, NotificationType.HOLD_EXPIRED, "시간이 지났어요");
		notify(consumer, NotificationType.HOLD_CREATED, "찜했어요");

		mockMvc.perform(patch("/notifications/" + notification.getId() + "/read")
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.readAt").isNotEmpty());

		mockMvc.perform(get("/notifications").header("Authorization", bearer(consumer)))
			.andExpect(jsonPath("$.data.unreadCount").value(1));
	}

	@Test
	void readingTheSameOneTwiceKeepsTheFirstMoment() throws Exception {
		Notification notification = notify(consumer, NotificationType.HOLD_EXPIRED, "시간이 지났어요");

		mockMvc.perform(patch("/notifications/" + notification.getId() + "/read")
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk());
		Instant firstRead = notificationRepository.findById(notification.getId()).orElseThrow().getReadAt();

		mockMvc.perform(patch("/notifications/" + notification.getId() + "/read")
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk());

		assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getReadAt())
			.isEqualTo(firstRead);
	}

	@Test
	void readingSomeoneElsesNotificationIsNotFoundRatherThanForbidden() throws Exception {
		Notification othersNotification =
			notify(newUser(UserRole.CONSUMER, "남"), NotificationType.HOLD_CREATED, "남의 알림");

		mockMvc.perform(patch("/notifications/" + othersNotification.getId() + "/read")
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

		assertThat(notificationRepository.findById(othersNotification.getId()).orElseThrow().getReadAt())
			.isNull();
	}

	@Test
	void readingAllEmptiesTheBadgeAndReportsWhatItTouched() throws Exception {
		notify(consumer, NotificationType.HOLD_CREATED, "찜했어요");
		notify(consumer, NotificationType.HOLD_EXPIRED, "시간이 지났어요");
		notify(newUser(UserRole.CONSUMER, "남"), NotificationType.HOLD_CREATED, "남의 알림");

		mockMvc.perform(post("/notifications/read-all").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.markedCount").value(2));

		mockMvc.perform(post("/notifications/read-all").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.markedCount").value(0));

		mockMvc.perform(get("/notifications").header("Authorization", bearer(consumer)))
			.andExpect(jsonPath("$.data.unreadCount").value(0));
		assertThat(notificationRepository.countByUserIdAndReadAtIsNull(consumer.getId())).isZero();
	}

	@Test
	void theOwnerAppReadsItsOwnInboxThroughTheSameEndpoint() throws Exception {
		User owner = newUser(UserRole.OWNER, "청과마을사장");
		notify(owner, NotificationType.NEW_HOLD_RECEIVED, "새 찜이 들어왔어요");

		mockMvc.perform(get("/notifications").header("Authorization", bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.unreadCount").value(1))
			.andExpect(jsonPath("$.data.notifications.content[0].type").value("NEW_HOLD_RECEIVED"));
	}

	@Test
	void aGuestHasNoInbox() throws Exception {
		mockMvc.perform(get("/notifications").header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	@Test
	void theInboxPaginates() throws Exception {
		notify(consumer, NotificationType.HOLD_CREATED, "하나");
		notify(consumer, NotificationType.HOLD_CREATED, "둘");
		notify(consumer, NotificationType.HOLD_CREATED, "셋");

		mockMvc.perform(get("/notifications?size=2").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.notifications.content.length()").value(2))
			.andExpect(jsonPath("$.data.notifications.totalElements").value(3))
			.andExpect(jsonPath("$.data.notifications.last").value(false))
			.andExpect(jsonPath("$.data.unreadCount")
				.value(3));
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
			TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private User newUser(UserRole role, String nickname) {
		return userRepository.saveAndFlush(new User(role, nickname, null, false, Instant.now()));
	}

	private Notification notify(User user, NotificationType type, String title) {
		return notificationRepository.saveAndFlush(
			new Notification(user, type, title, title + " 알림 본문", null));
	}
}
