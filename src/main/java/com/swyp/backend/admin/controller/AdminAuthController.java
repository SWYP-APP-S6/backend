package com.swyp.backend.admin.controller;

import com.swyp.backend.admin.dto.LoginRequest;
import com.swyp.backend.admin.dto.PasswordChangeRequest;
import com.swyp.backend.admin.dto.RefreshRequest;
import com.swyp.backend.admin.dto.TokenResponse;
import com.swyp.backend.admin.service.AdminAuthService;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/auth")
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.of(SuccessCode.OK, adminAuthService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ApiResponse.of(SuccessCode.OK, adminAuthService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
		adminAuthService.logout(request.refreshToken());
		return ApiResponse.of(SuccessCode.OK);
	}

	@PutMapping("/password")
	public ApiResponse<Void> changePassword(
			@AuthenticationPrincipal Long adminId,
			@Valid @RequestBody PasswordChangeRequest request) {
		adminAuthService.changePassword(adminId, request.currentPassword(), request.newPassword());
		return ApiResponse.of(SuccessCode.OK);
	}
}
