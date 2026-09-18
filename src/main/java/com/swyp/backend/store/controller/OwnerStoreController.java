package com.swyp.backend.store.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.store.dto.StoreDetailResponse;
import com.swyp.backend.store.dto.StoreRegisterRequest;
import com.swyp.backend.store.service.StoreService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OwnerStore", description = "점주 가게")
@RestController
@RequestMapping("/owner/stores")
@RequiredArgsConstructor
public class OwnerStoreController {

	private final StoreService storeService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<StoreDetailResponse> registerStore(
			@AuthenticationPrincipal Long ownerId, @Valid @RequestBody StoreRegisterRequest request) {
		return ApiResponse.of(SuccessCode.CREATED, storeService.registerStore(ownerId, request));
	}

	@GetMapping("/me")
	public ApiResponse<StoreDetailResponse> getMyStore(@AuthenticationPrincipal Long ownerId) {
		return ApiResponse.of(SuccessCode.OK, storeService.getMyStore(ownerId));
	}
}
