package com.swyp.backend.user.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.user.dto.KakaoLoginRequest;
import com.swyp.backend.user.dto.KakaoLoginResponse;
import com.swyp.backend.user.dto.RefreshRequest;
import com.swyp.backend.user.dto.SignupRequest;
import com.swyp.backend.user.dto.TokenResponse;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserAuthController {

	private final UserAuthService userAuthService;

	@PostMapping("/consumer/kakao")
	public ApiResponse<KakaoLoginResponse> loginAsConsumer(@Valid @RequestBody KakaoLoginRequest request) {
		return ApiResponse.of(
			SuccessCode.OK, userAuthService.loginWithKakao(UserRole.CONSUMER, request.kakaoAccessToken()));
	}

	@PostMapping("/owner/kakao")
	public ApiResponse<KakaoLoginResponse> loginAsOwner(@Valid @RequestBody KakaoLoginRequest request) {
		return ApiResponse.of(
			SuccessCode.OK, userAuthService.loginWithKakao(UserRole.OWNER, request.kakaoAccessToken()));
	}

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.of(SuccessCode.CREATED, userAuthService.signup(request)));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.of(SuccessCode.OK, userAuthService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
		userAuthService.logout(request.refreshToken());
		return ApiResponse.of(SuccessCode.OK);
	}
}
