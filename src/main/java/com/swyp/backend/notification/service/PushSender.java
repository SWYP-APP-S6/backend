package com.swyp.backend.notification.service;

import java.util.Map;

public interface PushSender {

	Result send(String fcmToken, PushMessage message);

	enum Result {
		DELIVERED,
		TOKEN_GONE,
		REJECTED,
		RETRYABLE,
		DISABLED
	}

	record PushMessage(String title, String body, Map<String, String> data) {
	}
}
