package com.swyp.backend.user.dto;

public record KakaoLoginResponse(
		boolean registered,
		String accessToken,
		String refreshToken,
		String signupToken) {

	public static KakaoLoginResponse registered(TokenResponse tokens) {
		return new KakaoLoginResponse(true, tokens.accessToken(), tokens.refreshToken(), null);
	}

	public static KakaoLoginResponse signupRequired(String signupToken) {
		return new KakaoLoginResponse(false, null, null, signupToken);
	}
}
