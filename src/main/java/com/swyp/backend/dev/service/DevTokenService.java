package com.swyp.backend.dev.service;

import com.swyp.backend.common.security.JwtProperties;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.dev.dto.DevTokenResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.function.UserFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev.test-token.enabled", havingValue = "true")
public class DevTokenService {

	private static final String PROVIDER = "dev";

	private final UserFunction userFunction;
	private final JwtTokenProvider tokenProvider;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	@Transactional
	public DevTokenResponse issue(UserRole role) {
		String providerId = providerId(role);
		User user = userFunction
				.findByOauthIdentity(PROVIDER, providerId, role)
				.orElseGet(() -> createTestUser(role, providerId));
		String accessToken =
				tokenProvider.createAccessToken(TokenRealm.USER, user.getId(), user.getRole().name());
		return new DevTokenResponse(
				user.getId(),
				user.getNickname(),
				user.getRole(),
				accessToken,
				jwtProperties.accessTtlFor(TokenRealm.USER).toSeconds());
	}

	private User createTestUser(UserRole role, String providerId) {
		User user = new User(role, nickname(role), null, false, Instant.now(clock));
		user.linkOauthAccount(PROVIDER, providerId);
		return userFunction.save(user);
	}

	private static String providerId(UserRole role) {
		return "test-" + role.name().toLowerCase(Locale.ROOT);
	}

	private static String nickname(UserRole role) {
		return role == UserRole.OWNER ? "테스트 점주" : "테스트 소비자";
	}
}
