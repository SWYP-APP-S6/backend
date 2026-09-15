package com.swyp.backend.hold.controller;

import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.hold.dto.OwnerHoldCancelCandidatesResponse;
import com.swyp.backend.hold.dto.OwnerHoldCancelRequest;
import com.swyp.backend.hold.dto.OwnerHoldDetailResponse;
import com.swyp.backend.hold.dto.OwnerHoldListResponse;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.service.OwnerHoldCancelService;
import com.swyp.backend.hold.service.OwnerHoldService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OwnerHold", description = "점주 찜 현황")
@RestController
@RequestMapping("/owner/holds")
@RequiredArgsConstructor
public class OwnerHoldController {

	private final OwnerHoldService ownerHoldService;
	private final OwnerHoldCancelService ownerHoldCancelService;

	@GetMapping
	@PageQueryParams
	public ApiResponse<OwnerHoldListResponse> getOwnerHolds(
			@AuthenticationPrincipal Long ownerId,
			@RequestParam(required = false) OwnerHoldStatus status,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, ownerHoldService.getHolds(ownerId, status, pageable));
	}

	@GetMapping("/cancel-candidates")
	public ApiResponse<OwnerHoldCancelCandidatesResponse> getHoldCancelCandidates(
			@AuthenticationPrincipal Long ownerId) {
		return ApiResponse.of(SuccessCode.OK, ownerHoldCancelService.getCancelCandidates(ownerId));
	}

	@PostMapping("/cancel")
	public ApiResponse<OwnerHoldCancelCandidatesResponse> cancelHoldsForShortage(
			@AuthenticationPrincipal Long ownerId, @Valid @RequestBody OwnerHoldCancelRequest request) {
		return ApiResponse.of(SuccessCode.OK, ownerHoldCancelService.cancelHolds(ownerId, request));
	}

	@GetMapping("/{id}")
	public ApiResponse<OwnerHoldDetailResponse> getOwnerHold(
			@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, ownerHoldService.getHold(ownerId, id));
	}

	@PostMapping("/{id}/complete")
	public ApiResponse<OwnerHoldDetailResponse> completePickup(
			@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, ownerHoldService.completePickup(ownerId, id));
	}
}
