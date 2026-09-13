package com.swyp.backend.hold;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hold")
public record HoldProperties(
		Duration ttl,
		int userQtyLimit,
		Duration expiryScanInterval,
		Duration expiryReminderLead,
		Duration noShowGrace,
		int cancelCreditMax,
		Duration cancelCreditRefill,
		Duration freeCancelWindow) {

	public HoldProperties {
		Objects.requireNonNull(ttl, "hold.ttl must be set");
		Objects.requireNonNull(expiryScanInterval, "hold.expiry-scan-interval must be set");
		Objects.requireNonNull(expiryReminderLead, "hold.expiry-reminder-lead must be set");
		Objects.requireNonNull(noShowGrace, "hold.no-show-grace must be set");
		Objects.requireNonNull(cancelCreditRefill, "hold.cancel-credit-refill must be set");
		Objects.requireNonNull(freeCancelWindow, "hold.free-cancel-window must be set");
		if (ttl.isNegative() || ttl.isZero()) {
			throw new IllegalArgumentException("hold.ttl must be positive");
		}
		if (expiryReminderLead.isNegative() || expiryReminderLead.isZero()) {
			throw new IllegalArgumentException("hold.expiry-reminder-lead must be positive");
		}
		if (userQtyLimit <= 0) {
			throw new IllegalArgumentException("hold.user-qty-limit must be positive");
		}
		if (cancelCreditMax <= 0) {
			throw new IllegalArgumentException("hold.cancel-credit-max must be positive");
		}
		if (cancelCreditRefill.isNegative() || cancelCreditRefill.isZero()) {
			throw new IllegalArgumentException("hold.cancel-credit-refill must be positive");
		}
	}
}
