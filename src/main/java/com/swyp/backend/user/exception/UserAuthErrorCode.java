package com.swyp.backend.user.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserAuthErrorCode implements ApiCode {

	INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "카카오 로그인 정보를 확인할 수 없습니다."),
	OAUTH_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 인증 서버와 통신할 수 없습니다."),
	INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 가입 토큰입니다."),
	ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
