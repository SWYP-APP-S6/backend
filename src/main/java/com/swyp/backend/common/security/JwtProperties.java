package com.swyp.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration guestAccessTtl, Duration refreshTtl) {

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException(
				"jwt.secret must be at least 32 bytes — set the JWT_SECRET environment variable");
		}
		Objects.requireNonNull(accessTtl, "jwt.access-ttl must be set");
		Objects.requireNonNull(guestAccessTtl, "jwt.guest-access-ttl must be set");
		Objects.requireNonNull(refreshTtl, "jwt.refresh-ttl must be set");
	}

	public Duration accessTtlFor(TokenRealm realm) {
		return realm == TokenRealm.GUEST ? guestAccessTtl : accessTtl;
	}
}
