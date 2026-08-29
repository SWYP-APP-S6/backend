package com.swyp.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private final JwtTokenProvider provider = new JwtTokenProvider(
		new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", Duration.ofMinutes(30), Duration.ofDays(14)));

	@Test
	void createAndParse_roundTripsSubjectRealmAndRole() {
		String token = provider.createAccessToken(TokenRealm.ADMIN, 42L, "SUPER");

		JwtTokenProvider.AccessToken accessToken = provider.parseAccessToken(token);

		assertThat(accessToken.principalId()).isEqualTo(42L);
		assertThat(accessToken.realm()).isEqualTo(TokenRealm.ADMIN);
		assertThat(accessToken.role()).isEqualTo("SUPER");
	}

	@Test
	void parseAccessToken_rejectsTamperedToken() {
		String token = provider.createAccessToken(TokenRealm.ADMIN, 1L, "SUPER");

		assertThatThrownBy(() -> provider.parseAccessToken(token + "tampered")).isInstanceOf(JwtException.class);
	}

	@Test
	void parseAccessToken_rejectsTokenOfAnotherType() {
		String signupToken = provider.createToken(
			"signup", "kakao-123", Map.of("nickname", "tester"), Duration.ofMinutes(10));

		assertThatThrownBy(() -> provider.parseAccessToken(signupToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void parse_rejectsAccessTokenPresentedAsAnotherType() {
		String accessToken = provider.createAccessToken(TokenRealm.USER, 7L, "CONSUMER");

		assertThatThrownBy(() -> provider.parse("signup", accessToken)).isInstanceOf(JwtException.class);
	}
}
