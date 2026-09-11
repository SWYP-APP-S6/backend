package com.swyp.backend.dev.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.dev.dto.DevTokenResponse;
import com.swyp.backend.dev.service.DevTokenService;
import com.swyp.backend.user.entity.UserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개발 전용")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
@ConditionalOnProperty(name = "dev.test-token.enabled", havingValue = "true")
public class DevTokenController {

	private final DevTokenService devTokenService;

	@PostMapping("/test-token")
	public ApiResponse<DevTokenResponse> issueTestToken(@RequestParam UserRole role) {
		return ApiResponse.of(SuccessCode.OK, devTokenService.issue(role));
	}
}
