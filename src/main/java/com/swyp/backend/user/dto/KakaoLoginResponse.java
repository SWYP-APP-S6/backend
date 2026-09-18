package com.swyp.backend.user.dto;

import org.jspecify.annotations.Nullable;

public record KakaoLoginResponse(
		boolean registered,
		@Nullable String accessToken,
		@Nullable String refreshToken,
		@Nullable String signupToken) {

	public static KakaoLoginResponse registered(TokenResponse tokens) {
		return new KakaoLoginResponse(true, tokens.accessToken(), tokens.refreshToken(), null);
	}

	public static KakaoLoginResponse signupRequired(String signupToken) {
		return new KakaoLoginResponse(false, null, null, signupToken);
	}
}
