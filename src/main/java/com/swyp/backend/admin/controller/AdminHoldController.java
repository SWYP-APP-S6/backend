package com.swyp.backend.admin.controller;

import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.hold.dto.AdminHoldQuery;
import com.swyp.backend.hold.dto.AdminHoldResponse;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.service.AdminHoldService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminHold", description = "관리자 찜 조회")
@RestController
@RequestMapping("/admin/holds")
@RequiredArgsConstructor
public class AdminHoldController {

	private final AdminHoldService adminHoldService;

	@GetMapping
	@PageQueryParams
	public ApiResponse<PageResponse<AdminHoldResponse>> getAdminHolds(
			@RequestParam(required = false) Long storeId,
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) HoldStatus status,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		AdminHoldQuery query = new AdminHoldQuery(storeId, productId, userId, status);
		return ApiResponse.of(SuccessCode.OK, adminHoldService.getHolds(query, pageable));
	}
}
