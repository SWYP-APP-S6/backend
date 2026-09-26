package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.NotificationPushState;

public record PushStateCount(NotificationPushState state, long count) {
}
