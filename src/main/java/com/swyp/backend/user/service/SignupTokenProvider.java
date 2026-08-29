package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SignupTokenProvider {

	private static final String TOKEN_TYPE = "signup";
	private static final String PROVIDER_CLAIM = "provider";
	private static final String NICKNAME_CLAIM = "nickname";
	private static final String ROLE_CLAIM = "role";

	private final JwtTokenProvider tokenProvider;
	private final Duration ttl;

	public SignupTokenProvider(JwtTokenProvider tokenProvider, @Value("${auth.signup-ttl}") Duration ttl) {
		this.tokenProvider = tokenProvider;
		this.ttl = ttl;
	}

	public String issue(String provider, String providerId, String nickname, UserRole role) {
		Map<String, String> claims = Map.of(
			PROVIDER_CLAIM, provider,
			NICKNAME_CLAIM, nickname,
			ROLE_CLAIM, role.name());
		return tokenProvider.createToken(TOKEN_TYPE, providerId, claims, ttl);
	}

	public SignupTicket parse(String signupToken) {
		try {
			Claims claims = tokenProvider.parse(TOKEN_TYPE, signupToken);
			String provider = claims.get(PROVIDER_CLAIM, String.class);
			String role = claims.get(ROLE_CLAIM, String.class);
			if (provider == null || role == null || claims.getSubject() == null) {
				throw new BusinessException(UserAuthErrorCode.INVALID_SIGNUP_TOKEN);
			}
			return new SignupTicket(
				provider,
				claims.getSubject(),
				claims.get(NICKNAME_CLAIM, String.class),
				UserRole.valueOf(role));
		} catch (JwtException | IllegalArgumentException e) {
			throw new BusinessException(UserAuthErrorCode.INVALID_SIGNUP_TOKEN);
		}
	}

	public record SignupTicket(String provider, String providerId, String nickname, UserRole role) {
	}
}
