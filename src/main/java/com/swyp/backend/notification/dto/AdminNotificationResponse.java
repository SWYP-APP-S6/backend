package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record AdminNotificationResponse(
		Long id,
		Recipient user,
		NotificationType type,
		String title,
		String body,
		@Nullable String deepLink,
		@Nullable Instant readAt,
		NotificationPushState pushState,
		int pushAttempts,
		@Nullable Instant pushedAt,
		Instant createdAt) {

	public record Recipient(Long id, String nickname) {}

	public static AdminNotificationResponse from(Notification notification) {
		return new AdminNotificationResponse(
				notification.getId(),
				new Recipient(notification.getUser().getId(), notification.getUser().getNickname()),
				notification.getType(),
				notification.getTitle(),
				notification.getBody(),
				notification.getDeepLink(),
				notification.getReadAt(),
				notification.getPushState(),
				notification.getPushAttempts(),
				notification.getPushedAt(),
				notification.getCreatedAt());
	}
}
