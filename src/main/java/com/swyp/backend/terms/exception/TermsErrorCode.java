package com.swyp.backend.terms.exception;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements ApiCode {

	TERMS_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "약관을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
