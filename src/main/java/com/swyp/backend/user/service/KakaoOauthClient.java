package com.swyp.backend.user.service;

import com.swyp.backend.user.entity.UserRole;

public interface KakaoOauthClient {

	Identity fetchIdentity(UserRole role, String kakaoAccessToken);

	record Identity(String providerId, String nickname) {
	}
}
