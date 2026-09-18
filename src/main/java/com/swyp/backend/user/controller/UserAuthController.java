package com.swyp.backend.user.controller;

import com.swyp.backend.common.openapi.ApiErrorCodes;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.common.security.AuthErrorCode;
import com.swyp.backend.user.dto.GuestTokenRequest;
import com.swyp.backend.user.dto.GuestTokenResponse;
import com.swyp.backend.user.dto.KakaoLoginRequest;
import com.swyp.backend.user.dto.KakaoLoginResponse;
import com.swyp.backend.user.dto.KakaoTokenExchangeRequest;
import com.swyp.backend.user.dto.KakaoTokenExchangeResponse;
import com.swyp.backend.user.dto.RefreshRequest;
import com.swyp.backend.user.dto.SignupRequest;
import com.swyp.backend.user.dto.TokenResponse;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.service.GuestTokenService;
import com.swyp.backend.user.service.UserAuthService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "앱 인증")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class UserAuthController {

	private final UserAuthService userAuthService;
	private final GuestTokenService guestTokenService;

	@PostMapping("/guest")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = "GUEST_ISSUE_LIMIT_EXCEEDED")
	public ApiResponse<GuestTokenResponse> issueGuestToken(@Valid @RequestBody GuestTokenRequest request) {
		return ApiResponse.of(SuccessCode.OK, guestTokenService.issue(request.installId()));
	}

	@PostMapping("/consumer/kakao")
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"INVALID_OAUTH_TOKEN", "OAUTH_PROVIDER_UNAVAILABLE"})
	public ApiResponse<KakaoLoginResponse> loginAsConsumer(@Valid @RequestBody KakaoLoginRequest request) {
		return ApiResponse.of(
			SuccessCode.OK, userAuthService.loginWithKakao(UserRole.CONSUMER, request.kakaoAccessToken()));
	}

	@PostMapping("/owner/kakao")
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"INVALID_OAUTH_TOKEN", "OAUTH_PROVIDER_UNAVAILABLE"})
	public ApiResponse<KakaoLoginResponse> loginAsOwner(@Valid @RequestBody KakaoLoginRequest request) {
		return ApiResponse.of(
			SuccessCode.OK, userAuthService.loginWithKakao(UserRole.OWNER, request.kakaoAccessToken()));
	}

	@PostMapping("/consumer/kakao/exchange")
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"INVALID_OAUTH_TOKEN", "OAUTH_PROVIDER_UNAVAILABLE"})
	public ApiResponse<KakaoTokenExchangeResponse> exchangeConsumerCode(
			@Valid @RequestBody KakaoTokenExchangeRequest request) {
		return ApiResponse.of(SuccessCode.OK,
			userAuthService.exchangeKakaoCode(UserRole.CONSUMER, request.code(), request.redirectUri()));
	}

	@PostMapping("/owner/kakao/exchange")
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"INVALID_OAUTH_TOKEN", "OAUTH_PROVIDER_UNAVAILABLE"})
	public ApiResponse<KakaoTokenExchangeResponse> exchangeOwnerCode(
			@Valid @RequestBody KakaoTokenExchangeRequest request) {
		return ApiResponse.of(SuccessCode.OK,
			userAuthService.exchangeKakaoCode(UserRole.OWNER, request.code(), request.redirectUri()));
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	@ApiErrorCodes(in = UserAuthErrorCode.class,
			codes = {"INVALID_SIGNUP_TOKEN", "ALREADY_REGISTERED"})
	public ApiResponse<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ApiResponse.of(SuccessCode.CREATED, userAuthService.signup(request));
	}

	@PostMapping("/refresh")
	@ApiErrorCodes(in = AuthErrorCode.class, codes = "INVALID_REFRESH_TOKEN")
	@ApiErrorCodes(in = UserAuthErrorCode.class, codes = "USER_NOT_FOUND")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.of(SuccessCode.OK, userAuthService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
		userAuthService.logout(request.refreshToken());
		return ApiResponse.of(SuccessCode.OK);
	}
}
