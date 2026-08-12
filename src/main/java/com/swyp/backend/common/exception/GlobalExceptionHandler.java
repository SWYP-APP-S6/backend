package com.swyp.backend.common.exception;

import com.swyp.backend.common.response.ApiCode;
import com.swyp.backend.common.response.ErrorCode;
import com.swyp.backend.common.response.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception mapping. Extends {@link ResponseEntityExceptionHandler} so Spring MVC's whole
 * family of client-error exceptions (wrong method → 405, unreadable body → 400, unsupported media
 * type → 415, …) map to their correct status instead of falling through to 500 — all rendered as
 * our {@link ErrorResponse} envelope via the {@link #createResponseEntity} override.
 *
 * <p>Feature code throws {@link BusinessException} with its own {@link ApiCode}. Framework errors
 * carry the HTTP status name as their {@code code}; validation failures add per-field messages.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		ApiCode code = e.getCode();
		log.warn("Business exception: {} - {}", code.name(), code.getMessage());
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
				.body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		return createResponseEntity(
				ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors), headers, status, request);
	}

	/**
	 * Single rendering point for every framework exception: swap the default ProblemDetail body for
	 * our envelope (built from the resolved status), unless a handler already supplied an envelope.
	 */
	@Override
	protected ResponseEntity<Object> createResponseEntity(
			Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		Object envelope = (body instanceof ErrorResponse) ? body : errorBody(statusCode);
		return new ResponseEntity<>(envelope, headers, statusCode);
	}

	private ErrorResponse errorBody(HttpStatusCode statusCode) {
		HttpStatus resolved = HttpStatus.resolve(statusCode.value());
		String code = (resolved != null) ? resolved.name() : "ERROR";
		String message = (resolved != null) ? resolved.getReasonPhrase() : "요청을 처리할 수 없습니다.";
		return new ErrorResponse(statusCode.value(), code, message, null);
	}
}
