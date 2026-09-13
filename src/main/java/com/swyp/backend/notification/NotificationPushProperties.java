package com.swyp.backend.notification;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.push")
public record NotificationPushProperties(
		Duration scanInterval, Duration staleAfter, int maxAttempts, int batchSize) {

	public NotificationPushProperties {
		Objects.requireNonNull(scanInterval, "notification.push.scan-interval must be set");
		Objects.requireNonNull(staleAfter, "notification.push.stale-after must be set");
		if (scanInterval.isNegative() || scanInterval.isZero()) {
			throw new IllegalArgumentException("notification.push.scan-interval must be positive");
		}
		if (staleAfter.isNegative() || staleAfter.isZero()) {
			throw new IllegalArgumentException("notification.push.stale-after must be positive");
		}
		if (maxAttempts <= 0) {
			throw new IllegalArgumentException("notification.push.max-attempts must be positive");
		}
		if (batchSize <= 0) {
			throw new IllegalArgumentException("notification.push.batch-size must be positive");
		}
	}
}
