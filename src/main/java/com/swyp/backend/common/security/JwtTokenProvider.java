package com.swyp.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies stateless access tokens (HMAC-signed JWT). The subject is the user id and the
 * {@code role} claim carries the authority. Refresh tokens are opaque and live in Redis (handled by
 * the refresh service), not here — so an access token is verified by signature alone, no store hit.
 */
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long accessTtlSeconds;

	public JwtTokenProvider(JwtProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTtlSeconds = properties.accessTtl().toSeconds();
	}

	public String createAccessToken(Long userId, String role) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("role", role)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
			.signWith(key)
			.compact();
	}

	/** Verifies signature and expiry; throws {@link io.jsonwebtoken.JwtException} if invalid or expired. */
	public Claims parse(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
