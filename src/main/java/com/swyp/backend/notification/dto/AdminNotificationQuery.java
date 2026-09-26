package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import org.jspecify.annotations.Nullable;

public record AdminNotificationQuery(
		@Nullable NotificationPushState pushState,
		@Nullable Long userId,
		@Nullable NotificationType type) {
}
