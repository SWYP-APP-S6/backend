package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.user.dto.KakaoLoginResponse;
import com.swyp.backend.user.dto.SignupRequest;
import com.swyp.backend.user.dto.TokenResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAuthService {

	private static final String PROVIDER_KAKAO = "kakao";
	private static final String FALLBACK_NICKNAME_PREFIX = "맹그로회원";
	private static final int NICKNAME_MAX_LENGTH = 50;

	private final KakaoOauthClient kakaoOauthClient;
	private final SignupTokenProvider signupTokenProvider;
	private final UserRepository userRepository;
	private final JwtTokenProvider tokenProvider;
	private final RefreshTokenService refreshTokenService;

	public KakaoLoginResponse loginWithKakao(UserRole role, String kakaoAccessToken) {
		KakaoOauthClient.Identity identity = kakaoOauthClient.fetchIdentity(role, kakaoAccessToken);
		return userRepository
			.findByOauthProviderAndOauthProviderIdAndRole(PROVIDER_KAKAO, identity.providerId(), role)
			.map(user -> KakaoLoginResponse.registered(issueTokensFor(user)))
			.orElseGet(() -> KakaoLoginResponse.signupRequired(signupTokenProvider.issue(
				PROVIDER_KAKAO, identity.providerId(), nicknameFor(identity), role)));
	}

	@Transactional
	public TokenResponse signup(SignupRequest request) {
		SignupTokenProvider.SignupTicket ticket = signupTokenProvider.parse(request.signupToken());
		if (userRepository.findByOauthProviderAndOauthProviderIdAndRole(
				ticket.provider(), ticket.providerId(), ticket.role()).isPresent()) {
			throw new BusinessException(UserAuthErrorCode.ALREADY_REGISTERED);
		}
		User user = new User(ticket.role(), ticket.nickname(), null, request.marketingOptIn(), Instant.now());
		user.linkOauthAccount(ticket.provider(), ticket.providerId());
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(UserAuthErrorCode.ALREADY_REGISTERED);
		}
		return issueTokensFor(user);
	}

	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(TokenRealm.USER, refreshToken);
		User user = userRepository.findById(rotation.principalId())
			.orElseThrow(() -> new BusinessException(UserAuthErrorCode.USER_NOT_FOUND));
		return new TokenResponse(accessTokenFor(user), rotation.token());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(TokenRealm.USER, refreshToken);
	}

	private TokenResponse issueTokensFor(User user) {
		return new TokenResponse(accessTokenFor(user), refreshTokenService.issue(TokenRealm.USER, user.getId()));
	}

	private String accessTokenFor(User user) {
		return tokenProvider.createAccessToken(TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private String nicknameFor(KakaoOauthClient.Identity identity) {
		String nickname = identity.nickname() == null ? null : identity.nickname().strip();
		if (nickname == null || nickname.isEmpty()) {
			String providerId = identity.providerId();
			return FALLBACK_NICKNAME_PREFIX + providerId.substring(Math.max(0, providerId.length() - 4));
		}
		return nickname.length() > NICKNAME_MAX_LENGTH ? nickname.substring(0, NICKNAME_MAX_LENGTH) : nickname;
	}
}
