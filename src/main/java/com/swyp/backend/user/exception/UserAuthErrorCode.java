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
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	HOLDING_HOLDS_REMAIN(HttpStatus.CONFLICT, "진행 중인 찜이 있어 탈퇴할 수 없습니다. 찜을 모두 처리한 뒤 다시 시도해 주세요."),
	STORE_HOLDING_HOLDS_REMAIN(HttpStatus.CONFLICT, "가게에 처리되지 않은 찜이 있어 탈퇴할 수 없습니다. 찜을 모두 처리한 뒤 다시 시도해 주세요."),
	TESTER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "테스트 모드를 쓸 수 없는 계정입니다. 관리자에게 테스트 허가를 요청해 주세요."),
	TESTER_NEEDS_KAKAO_ACCOUNT(HttpStatus.CONFLICT, "카카오로 가입한 계정에만 테스트를 허가할 수 있습니다."),
	TESTER_PERMISSION_ON_TEST_ACCOUNT(HttpStatus.CONFLICT, "테스트 계정에는 허가를 줄 수 없습니다. 같은 사람의 실제 계정에 허가해 주세요."),
	GUEST_ISSUE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "비회원 토큰 발급 한도를 초과했습니다.");

	private final HttpStatus status;
	private final String message;
}
