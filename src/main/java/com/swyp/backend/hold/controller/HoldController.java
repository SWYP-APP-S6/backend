package com.swyp.backend.hold.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.hold.dto.ActiveHoldResponse;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.dto.HoldDetailResponse;
import com.swyp.backend.hold.dto.HoldHistoryResponse;
import com.swyp.backend.hold.service.HoldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Hold", description = "찜")
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

	@GetMapping
	public ApiResponse<HoldHistoryResponse> getHolds(
			@AuthenticationPrincipal Long userId, @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, holdService.getHolds(userId, pageable));
	}

	@GetMapping("/active")
	public ApiResponse<ActiveHoldResponse> getActiveHold(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, holdService.getActiveHold(userId));
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
