package com.swyp.backend.admin.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.store.dto.StoreDetailResponse;
import com.swyp.backend.store.dto.StoreStatusUpdateRequest;
import com.swyp.backend.store.dto.StoreSummaryResponse;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stores")
@RequiredArgsConstructor
public class AdminStoreController {

	private final StoreService storeService;

	@GetMapping
	public ApiResponse<PageResponse<StoreSummaryResponse>> getStores(
			@RequestParam(required = false) StoreStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, storeService.getStores(status, pageable));
	}

	@GetMapping("/{id}")
	public ApiResponse<StoreDetailResponse> getStore(@PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, storeService.getStore(id));
	}

	@PatchMapping("/{id}/status")
	public ApiResponse<Void> updateStatus(
			@PathVariable Long id, @Valid @RequestBody StoreStatusUpdateRequest request) {
		storeService.updateStatus(id, request.status());
		return ApiResponse.of(SuccessCode.OK);
	}
}
