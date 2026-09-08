package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.JwtProperties;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.user.dto.GuestTokenResponse;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class GuestTokenService {

	static final String GUEST_ROLE = "GUEST";
	static final String GUEST_KEY_PREFIX = "guest:";
	static final String ISSUE_KEY_PREFIX = "guest-issue:";
	private static final Duration ISSUE_WINDOW = Duration.ofDays(1);

	private final JwtTokenProvider tokenProvider;
	private final StringRedisTemplate redis;
	private final Duration ttl;
	private final int issueLimitPerDay;
	private final SecureRandom random = new SecureRandom();

	public GuestTokenService(
			JwtTokenProvider tokenProvider,
			StringRedisTemplate redis,
			JwtProperties jwtProperties,
			@Value("${auth.guest-issue-limit-per-day}") int issueLimitPerDay) {
		this.tokenProvider = tokenProvider;
		this.redis = redis;
		this.ttl = jwtProperties.accessTtlFor(TokenRealm.GUEST);
		this.issueLimitPerDay = issueLimitPerDay;
	}

	public GuestTokenResponse issue(String installId) {
		enforceIssueLimit(installId);
		long guestId = random.nextLong(1, Long.MAX_VALUE);
		redis.opsForValue().set(GUEST_KEY_PREFIX + guestId, installId, ttl);
		return new GuestTokenResponse(tokenProvider.createAccessToken(TokenRealm.GUEST, guestId, GUEST_ROLE));
	}

	private void enforceIssueLimit(String installId) {
		String key = ISSUE_KEY_PREFIX + installId;
		redis.opsForValue().setIfAbsent(key, "0", ISSUE_WINDOW);
		Long count = redis.opsForValue().increment(key);
		if (count != null && count > issueLimitPerDay) {
			throw new BusinessException(UserAuthErrorCode.GUEST_ISSUE_LIMIT_EXCEEDED);
		}
	}
}
