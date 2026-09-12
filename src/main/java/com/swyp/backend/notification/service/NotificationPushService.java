package com.swyp.backend.notification.service;

import com.swyp.backend.notification.NotificationPushProperties;
import com.swyp.backend.notification.dto.PendingPush;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.notification.function.DeviceTokenFunction;
import com.swyp.backend.notification.function.NotificationFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

	private final NotificationFunction notificationFunction;
	private final DeviceTokenFunction deviceTokenFunction;
	private final NotificationPusher notificationPusher;
	private final PushSender pushSender;
	private final NotificationPushProperties pushProperties;
	private final Clock clock;

	@Scheduled(
			fixedDelayString = "${notification.push.scan-interval}",
			initialDelayString = "${notification.push.scan-interval}")
	public int dispatchPendingPushes() {
		Instant staleBefore = Instant.now(clock).minus(pushProperties.staleAfter());
		int delivered = 0;
		for (PendingPush pending : notificationFunction.findPendingPushes(pushProperties.batchSize())) {
			delivered += dispatchQuietly(pending, staleBefore) ? 1 : 0;
		}
		if (delivered > 0) {
			log.info("Pushed {} notifications", delivered);
		}
		return delivered;
	}

	private boolean dispatchQuietly(PendingPush pending, Instant staleBefore) {
		try {
			return dispatch(pending, staleBefore);
		} catch (RuntimeException e) {
			log.error("Notification {} could not be dispatched", pending.notificationId(), e);
			recordAttemptQuietly(pending.notificationId());
			return false;
		}
	}

	private void recordAttemptQuietly(Long notificationId) {
		try {
			notificationPusher.recordAttempt(notificationId);
		} catch (RuntimeException e) {
			log.error("Notification {} could not even be marked as attempted -- it will be picked "
					+ "up again next pass", notificationId, e);
		}
	}

	private boolean dispatch(PendingPush pending, Instant staleBefore) {
		if (pending.createdAt().isBefore(staleBefore)) {
			log.info("Notification {} is older than the push deadline -- leaving it in the inbox only",
					pending.notificationId());
			notificationPusher.skip(pending.notificationId());
			return false;
		}
		List<UserDeviceToken> deviceTokens = deviceTokenFunction.findTokensOf(pending.userId());
		if (deviceTokens.isEmpty()) {
			notificationPusher.skip(pending.notificationId());
			return false;
		}
		return deliver(pending, deviceTokens);
	}

	private boolean deliver(PendingPush pending, List<UserDeviceToken> deviceTokens) {
		PushSender.PushMessage message = messageOf(pending);
		List<String> deadTokens = new ArrayList<>();
		boolean anyDelivered = false;
		boolean anyRetryable = false;
		boolean anyRejected = false;
		boolean disabled = false;

		for (UserDeviceToken deviceToken : deviceTokens) {
			switch (pushSender.send(deviceToken.getFcmToken(), message)) {
				case DELIVERED -> anyDelivered = true;
				case TOKEN_GONE -> deadTokens.add(deviceToken.getFcmToken());
				case RETRYABLE -> anyRetryable = true;
				case REJECTED -> anyRejected = true;
				case DISABLED -> disabled = true;
			}
		}
		notificationPusher.dropDeadTokens(deadTokens);

		if (anyDelivered) {
			notificationPusher.markDelivered(pending.notificationId());
			return true;
		}
		if (!disabled && anyRetryable) {
			notificationPusher.recordAttempt(pending.notificationId());
			return false;
		}
		if (!disabled && anyRejected) {
			notificationPusher.fail(pending.notificationId());
			return false;
		}
		notificationPusher.skip(pending.notificationId());
		return false;
	}

	private static PushSender.PushMessage messageOf(PendingPush pending) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", pending.type().name());
		data.put("notificationId", String.valueOf(pending.notificationId()));
		if (pending.deepLink() != null) {
			data.put("deepLink", pending.deepLink());
		}
		return new PushSender.PushMessage(pending.title(), pending.body(), data);
	}
}
