package com.swyp.backend.common.response;

import org.springframework.http.HttpStatus;

/**
 * Contract for a machine-readable API code: HTTP status + stable code name + human message. Error
 * enums implement this so thrown exceptions and the error envelope share one source of truth.
 */
public interface ApiCode {

	HttpStatus getStatus();

	String getMessage();

	/** Stable code string sent to clients. Enum {@code name()} satisfies this. */
	String name();
}
