package com.swyp.backend.user.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.RefreshTokenService;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.function.TermsFunction;
import com.swyp.backend.user.dto.KakaoLoginResponse;
import com.swyp.backend.user.dto.KakaoTokenExchangeResponse;
import com.swyp.backend.user.dto.SignupRequest;
import com.swyp.backend.user.dto.TokenResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.function.UserFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAuthService {

	private static final String PROVIDER_KAKAO = "kakao";
	private static final String FALLBACK_NICKNAME_PREFIX = "맹그로회원";
	private static final int NICKNAME_MAX_LENGTH = 50;

	private final KakaoOauthClient kakaoOauthClient;
	private final SignupTokenProvider signupTokenProvider;
	private final UserFunction userFunction;
	private final JwtTokenProvider tokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final TermsFunction termsFunction;
	private final Clock clock;

	public KakaoTokenExchangeResponse exchangeKakaoCode(UserRole role, String code, String redirectUri) {
		return new KakaoTokenExchangeResponse(kakaoOauthClient.exchangeAuthorizationCode(role, code, redirectUri));
	}

	public KakaoLoginResponse loginWithKakao(UserRole role, String kakaoAccessToken) {
		KakaoOauthClient.Identity identity = kakaoOauthClient.fetchIdentity(role, kakaoAccessToken);
		return userFunction
			.findByOauthIdentity(PROVIDER_KAKAO, identity.providerId(), role)
			.map(user -> KakaoLoginResponse.registered(issueTokensFor(user)))
			.orElseGet(() -> KakaoLoginResponse.signupRequired(signupTokenProvider.issue(
				PROVIDER_KAKAO, identity.providerId(), nicknameFor(identity), role)));
	}

	@Transactional
	public TokenResponse signup(SignupRequest request) {
		SignupTokenProvider.SignupTicket ticket = signupTokenProvider.parse(request.signupToken());
		if (userFunction.findByOauthIdentity(
				ticket.provider(), ticket.providerId(), ticket.role()).isPresent()) {
			throw new BusinessException(UserAuthErrorCode.ALREADY_REGISTERED);
		}
		Set<TermsType> agreedTypes = agreedTypesOf(request);
		if (!agreedTypes.containsAll(termsFunction.findRequiredTypesOf(ticket.role()))) {
			throw new BusinessException(UserAuthErrorCode.TERMS_AGREEMENT_REQUIRED);
		}
		Instant now = Instant.now(clock);
		User user = new User(ticket.role(), ticket.nickname(), null, request.marketingOptIn(), now);
		user.linkOauthAccount(ticket.provider(), ticket.providerId());
		userFunction.save(user);
		recordTermsAgreements(user, agreedTypes, now);
		return issueTokensFor(user);
	}

	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(TokenRealm.USER, refreshToken);
		User user = userFunction.getById(rotation.principalId());
		return new TokenResponse(accessTokenFor(user), rotation.token());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(TokenRealm.USER, refreshToken);
	}

	private static Set<TermsType> agreedTypesOf(SignupRequest request) {
		Set<TermsType> agreedTypes = EnumSet.noneOf(TermsType.class);
		if (request.serviceTermsAgreed()) {
			agreedTypes.add(TermsType.SERVICE);
		}
		if (request.privacyTermsAgreed()) {
			agreedTypes.add(TermsType.PRIVACY_COLLECTION);
		}
		if (request.locationTermsAgreed()) {
			agreedTypes.add(TermsType.LOCATION);
		}
		if (request.thirdPartyTermsAgreed()) {
			agreedTypes.add(TermsType.THIRD_PARTY);
		}
		if (request.marketingOptIn()) {
			agreedTypes.add(TermsType.MARKETING);
		}
		return agreedTypes;
	}

	private void recordTermsAgreements(User user, Set<TermsType> agreedTypes, Instant agreedAt) {
		if (termsFunction.recordAgreements(user, agreedTypes, agreedAt).isEmpty()) {
			log.warn("No terms documents are published for {} -- user {} signed up without an agreement record",
				user.getRole(), user.getId());
		}
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
