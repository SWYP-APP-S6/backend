package com.swyp.backend.hold.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.service.HoldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "찜")
@RestController
@RequiredArgsConstructor
@RequestMapping("/holds")
public class HoldController {

	private final HoldService holdService;

	@PostMapping
	public ResponseEntity<ApiResponse<HoldDetailResponse>> createHold(
			@AuthenticationPrincipal Long userId, @Valid @RequestBody HoldCreateRequest request) {
		HoldDetailResponse response = holdService.create(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(SuccessCode.CREATED, response));
	}

	@GetMapping("/active")
	public ApiResponse<List<HoldDetailResponse>> getActiveHolds(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, holdService.getActiveHolds(userId));
	}

	@GetMapping("/{holdId}")
	public ApiResponse<HoldDetailResponse> getHold(
			@AuthenticationPrincipal Long userId, @PathVariable Long holdId) {
		return ApiResponse.of(SuccessCode.OK, holdService.getHold(userId, holdId));
	}

	@PostMapping("/{holdId}/cancel")
	public ApiResponse<HoldDetailResponse> cancelHold(
			@AuthenticationPrincipal Long userId, @PathVariable Long holdId) {
		return ApiResponse.of(SuccessCode.OK, holdService.cancel(userId, holdId));
	}
}
