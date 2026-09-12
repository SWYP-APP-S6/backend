package com.swyp.backend.notification.dto;

import com.swyp.backend.common.response.PageResponse;

public record NotificationsResponse(
		long unreadCount, PageResponse<NotificationResponse> notifications) {}
