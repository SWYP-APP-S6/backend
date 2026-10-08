package com.swyp.backend.user.controller;

import com.swyp.backend.common.openapi.ApiErrorCodes;
import com.swyp.backend.common.security.AuthErrorCode;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.user.dto.MeResponse;
import com.swyp.backend.user.dto.MyLocationResponse;
import com.swyp.backend.user.dto.MyLocationUpdateRequest;
import com.swyp.backend.user.dto.TestModeSwitchRequest;
import com.swyp.backend.user.dto.TokenResponse;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.service.UserAuthService;
import com.swyp.backend.user.service.UserDeletionService;
import com.swyp.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
	private final UserAuthService userAuthService;

	@GetMapping("/me")
	public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, userService.getMe(userId));
	}

	@PatchMapping("/me/test-mode")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = {"TESTER_NOT_ALLOWED"})
	@ApiErrorCodes(in = AuthErrorCode.class, codes = {"INVALID_REFRESH_TOKEN"})
	public ApiResponse<TokenResponse> switchMyTestMode(
			@AuthenticationPrincipal Long userId, @Valid @RequestBody TestModeSwitchRequest request) {
		return ApiResponse.of(
				SuccessCode.OK, userAuthService.switchTestMode(userId, request.on(), request.refreshToken()));
	}

	@DeleteMapping("/me")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = {"HOLDING_HOLDS_REMAIN", "STORE_HOLDING_HOLDS_REMAIN"})
	public ApiResponse<Void> deleteMe(@AuthenticationPrincipal Long userId) {
		userDeletionService.delete(userId);
		return ApiResponse.of(SuccessCode.OK);
	}

	@GetMapping("/me/location")
	public ApiResponse<MyLocationResponse> getMyLocation(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, userService.getMyLocation(userId));
	}

	@PutMapping("/me/location")
	public ApiResponse<MyLocationResponse> setMyLocation(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody MyLocationUpdateRequest request) {
		return ApiResponse.of(SuccessCode.OK, userService.setMyLocation(userId, request));
	}
}
