package com.swyp.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private final JwtTokenProvider provider = new JwtTokenProvider(
		new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", Duration.ofMinutes(30), Duration.ofDays(14)));

	@Test
	void createAndParse_roundTripsSubjectAndRole() {
		String token = provider.createAccessToken(42L, "ADMIN");

		Claims claims = provider.parse(token);
		assertThat(claims.getSubject()).isEqualTo("42");
		assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
	}

	@Test
	void parse_rejectsTamperedToken() {
		String token = provider.createAccessToken(1L, "ADMIN");

		assertThatThrownBy(() -> provider.parse(token + "tampered")).isInstanceOf(JwtException.class);
	}
}
