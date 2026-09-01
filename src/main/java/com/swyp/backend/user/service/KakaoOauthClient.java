package com.swyp.backend.user.service;

import com.swyp.backend.user.entity.UserRole;

public interface KakaoOauthClient {

	Identity fetchIdentity(UserRole role, String kakaoAccessToken);

	String exchangeAuthorizationCode(UserRole role, String code, String redirectUri);

	record Identity(String providerId, String nickname) {
	}
}
