package com.swyp.backend.user.controller;

import com.swyp.backend.common.openapi.ApiErrorCodes;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.user.dto.MeResponse;
import com.swyp.backend.user.dto.MyLocationResponse;
import com.swyp.backend.user.dto.MyLocationUpdateRequest;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.service.UserDeletionService;
import com.swyp.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final UserDeletionService userDeletionService;

	@GetMapping("/me")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = "USER_NOT_FOUND")
	public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, userService.getMe(userId));
	}

	@DeleteMapping("/me")
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"USER_NOT_FOUND", "HOLDING_HOLDS_REMAIN", "STORE_HOLDING_HOLDS_REMAIN"})
	public ApiResponse<Void> deleteMe(@AuthenticationPrincipal Long userId) {
		userDeletionService.delete(userId);
		return ApiResponse.of(SuccessCode.OK);
	}

	@GetMapping("/me/location")
	public ApiResponse<MyLocationResponse> getMyLocation(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, userService.getMyLocation(userId));
	}

	@PutMapping("/me/location")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = "USER_NOT_FOUND")
	public ApiResponse<MyLocationResponse> setMyLocation(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody MyLocationUpdateRequest request) {
		return ApiResponse.of(SuccessCode.OK, userService.setMyLocation(userId, request));
	}
}
