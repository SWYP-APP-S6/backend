package com.swyp.backend.store.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.store.dto.NearbyStoresRequest;
import com.swyp.backend.store.dto.NearbyStoresResponse;
import com.swyp.backend.store.dto.StoreProductsResponse;
import com.swyp.backend.store.service.StoreService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stores")
public class StoreController {

	private final StoreService storeService;

	@GetMapping("/nearby")
	public ApiResponse<NearbyStoresResponse> getNearbyStores(
			@Valid @ParameterObject @ModelAttribute NearbyStoresRequest request) {
		return ApiResponse.of(SuccessCode.OK, storeService.findNearbyStores(request));
	}

	@GetMapping("/{storeId}/products")
	public ApiResponse<StoreProductsResponse> getStoreProducts(
			@PathVariable Long storeId,
			@RequestParam(required = false) BigDecimal lat,
			@RequestParam(required = false) BigDecimal lng) {
		return ApiResponse.of(SuccessCode.OK, storeService.getStoreProducts(storeId, lat, lng));
	}
}
