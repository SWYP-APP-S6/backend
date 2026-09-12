package com.swyp.backend.notification.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ApiCode {

	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
	DEVICE_TOKEN_CONFLICT(HttpStatus.CONFLICT, "기기 토큰이 방금 다른 요청으로 등록됐습니다. 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;
}
