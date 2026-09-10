package com.swyp.backend.store.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.store.dto.NearbyStoresRequest;
import com.swyp.backend.store.dto.NearbyStoresResponse;
import com.swyp.backend.store.dto.StoreProductsResponse;
import com.swyp.backend.store.dto.StoreProductsRequest;
import com.swyp.backend.store.service.StoreBrowseService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가게 탐색")
@RestController
@RequiredArgsConstructor
@RequestMapping("/stores")
public class StoreController {

	private final StoreBrowseService storeBrowseService;

	@GetMapping("/nearby")
	public ApiResponse<NearbyStoresResponse> getNearbyStores(
			@Valid @ParameterObject @ModelAttribute NearbyStoresRequest request) {
		return ApiResponse.of(SuccessCode.OK, storeBrowseService.findNearbyStores(request));
	}

	@GetMapping("/{storeId}/products")
	public ApiResponse<StoreProductsResponse> getStoreProducts(
			@PathVariable Long storeId,
			@Valid @ParameterObject @ModelAttribute StoreProductsRequest request) {
		return ApiResponse.of(SuccessCode.OK, storeBrowseService.getStoreProducts(storeId, request));
	}
}
