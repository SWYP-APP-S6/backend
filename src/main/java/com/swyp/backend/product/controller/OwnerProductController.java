package com.swyp.backend.product.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.product.dto.OwnerHomeResponse;
import com.swyp.backend.product.dto.ProductAvailableQtyUpdateRequest;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.service.ProductService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "점주 상품")
@RestController
@RequestMapping("/owner/products")
@RequiredArgsConstructor
public class OwnerProductController {

	private final ProductService productService;

	@PostMapping
	public ResponseEntity<ApiResponse<ProductDetailResponse>> registerProduct(
			@AuthenticationPrincipal Long ownerId, @Valid @RequestBody ProductRegisterRequest request) {
		ProductDetailResponse response = productService.registerProduct(ownerId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessCode.CREATED, response));
	}

	@GetMapping
	public ApiResponse<OwnerHomeResponse> getHome(@AuthenticationPrincipal Long ownerId) {
		return ApiResponse.of(SuccessCode.OK, productService.getHome(ownerId));
	}

	@GetMapping("/{id}")
	public ApiResponse<ProductDetailResponse> getMyProduct(
			@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, productService.getMyProduct(ownerId, id));
	}

	@PatchMapping("/{id}/available-qty")
	public ApiResponse<ProductDetailResponse> updateAvailableQty(
			@AuthenticationPrincipal Long ownerId,
			@PathVariable Long id,
			@Valid @RequestBody ProductAvailableQtyUpdateRequest request) {
		return ApiResponse.of(SuccessCode.OK, productService.updateAvailableQty(ownerId, id, request));
	}
}
