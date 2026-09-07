package com.swyp.backend.store.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.common.security.CurrentUser;
import com.swyp.backend.store.dto.StoreDetailResponse;
import com.swyp.backend.store.dto.StoreRegisterRequest;
import com.swyp.backend.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/owner/stores")
@RequiredArgsConstructor
public class OwnerStoreController {

	private final StoreService storeService;

	@PostMapping
	public ResponseEntity<ApiResponse<StoreDetailResponse>> registerStore(
			Authentication authentication, @Valid @RequestBody StoreRegisterRequest request) {
		StoreDetailResponse response = storeService.registerStore(CurrentUser.id(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessCode.CREATED, response));
	}

	@GetMapping("/me")
	public ApiResponse<StoreDetailResponse> getMyStore(Authentication authentication) {
		return ApiResponse.of(SuccessCode.OK, storeService.getMyStore(CurrentUser.id(authentication)));
	}
}
