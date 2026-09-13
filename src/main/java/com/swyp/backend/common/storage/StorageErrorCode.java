package com.swyp.backend.common.storage;

import com.swyp.backend.common.response.ApiCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StorageErrorCode implements ApiCode {

	UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "jpg 또는 png 사진만 올릴 수 있습니다."),
	IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량이 너무 큽니다."),
	IMAGE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사진을 저장하지 못했습니다.");

	private final HttpStatus status;
	private final String message;
}
