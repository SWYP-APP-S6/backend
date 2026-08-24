package com.swyp.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

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

	public Claims parse(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
