package com.swyp.backend.common.security;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(PerMinute perMinute) {

	public RateLimitProperties {
		Objects.requireNonNull(perMinute, "ratelimit.per-minute.{guest,user,admin} must be set");
	}

	public int limitFor(TokenRealm realm) {
		return switch (realm) {
			case GUEST -> perMinute.guest();
			case USER -> perMinute.user();
			case ADMIN -> perMinute.admin();
		};
	}

	public record PerMinute(int guest, int user, int admin) {

		public PerMinute {
			if (guest <= 0 || user <= 0 || admin <= 0) {
				throw new IllegalArgumentException("ratelimit.per-minute.* must be positive");
			}
		}
	}
}
