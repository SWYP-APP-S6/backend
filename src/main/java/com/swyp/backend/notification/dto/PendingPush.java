package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.NotificationType;
import java.time.Instant;

public record PendingPush(
		Long notificationId,
		Long userId,
		NotificationType type,
		String title,
		String body,
		String deepLink,
		Instant createdAt) {
}
