package com.swyp.backend.hold;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hold")
public record HoldProperties(Duration ttl, int userQtyLimit, Duration expiryScanInterval) {

	public HoldProperties {
		Objects.requireNonNull(ttl, "hold.ttl must be set");
		Objects.requireNonNull(expiryScanInterval, "hold.expiry-scan-interval must be set");
		if (ttl.isNegative() || ttl.isZero()) {
			throw new IllegalArgumentException("hold.ttl must be positive");
		}
		if (userQtyLimit <= 0) {
			throw new IllegalArgumentException("hold.user-qty-limit must be positive");
		}
	}
}
