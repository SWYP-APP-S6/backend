package com.swyp.backend.notification.service;

import com.swyp.backend.notification.NotificationPushProperties;
import com.swyp.backend.notification.function.DeviceTokenFunction;
import com.swyp.backend.notification.function.NotificationFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationPusher {

	private final NotificationFunction notificationFunction;
	private final DeviceTokenFunction deviceTokenFunction;
	private final NotificationPushProperties pushProperties;
	private final Clock clock;

	@Transactional
	public void markDelivered(Long notificationId) {
		notificationFunction.getById(notificationId).markPushDelivered(Instant.now(clock));
	}

	@Transactional
	public void skip(Long notificationId) {
		notificationFunction.getById(notificationId).skipPush();
	}

	@Transactional
	public void fail(Long notificationId) {
		notificationFunction.getById(notificationId).failPush();
	}

	@Transactional
	public void recordAttempt(Long notificationId) {
		notificationFunction.getById(notificationId)
				.recordPushAttempt(pushProperties.maxAttempts());
	}

	@Transactional
	public void dropDeadTokens(List<String> fcmTokens) {
		if (fcmTokens.isEmpty()) {
			return;
		}
		deviceTokenFunction.deleteTokens(fcmTokens);
	}
}
