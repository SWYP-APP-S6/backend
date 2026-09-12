package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record NotificationResponse(
		Long id,
		NotificationType type,
		String title,
		String body,
		@Nullable String deepLink,
		@Nullable Instant readAt,
		Instant notifiedAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getBody(),
				notification.getDeepLink(),
				notification.getReadAt(),
				notification.getCreatedAt());
	}
}
