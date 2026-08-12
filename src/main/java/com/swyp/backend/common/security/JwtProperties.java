package com.swyp.backend.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. {@code secret} must be at least 32 bytes (HMAC-SHA256) — supply it via the
 * {@code JWT_SECRET} env var in real environments; the committed default is dev-only.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
