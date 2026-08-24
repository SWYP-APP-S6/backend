package com.swyp.backend.common.response;

import org.springframework.http.HttpStatus;

public interface ApiCode {

	HttpStatus getStatus();

	String getMessage();

	String name();
}
