package com.swyp.backend.notification.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.notification.dto.DeviceTokenDeleteRequest;
import com.swyp.backend.notification.dto.DeviceTokenRegisterRequest;
import com.swyp.backend.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications/device-tokens")
public class DeviceTokenController {

	private final DeviceTokenService deviceTokenService;

	@PostMapping
	public ApiResponse<Void> registerDeviceToken(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody DeviceTokenRegisterRequest request) {
		deviceTokenService.register(userId, request);
		return ApiResponse.of(SuccessCode.OK);
	}

	@DeleteMapping
	public ApiResponse<Void> unregisterDeviceToken(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody DeviceTokenDeleteRequest request) {
		deviceTokenService.unregister(userId, request);
		return ApiResponse.of(SuccessCode.OK);
	}
}
