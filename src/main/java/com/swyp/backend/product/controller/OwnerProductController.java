package com.swyp.backend.product.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.common.security.CurrentUser;
import com.swyp.backend.product.dto.ProductAvailableQtyUpdateRequest;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.dto.ProductSummaryResponse;
import com.swyp.backend.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/owner/products")
@RequiredArgsConstructor
public class OwnerProductController {

	private final ProductService productService;

	@PostMapping
	public ResponseEntity<ApiResponse<ProductDetailResponse>> registerProduct(
			Authentication authentication, @Valid @RequestBody ProductRegisterRequest request) {
		ProductDetailResponse response = productService.registerProduct(CurrentUser.id(authentication), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(SuccessCode.CREATED, response));
	}

	@GetMapping
	public ApiResponse<List<ProductSummaryResponse>> getMyProducts(Authentication authentication) {
		return ApiResponse.of(SuccessCode.OK, productService.getMyProducts(CurrentUser.id(authentication)));
	}

	@GetMapping("/{id}")
	public ApiResponse<ProductDetailResponse> getMyProduct(Authentication authentication, @PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, productService.getMyProduct(CurrentUser.id(authentication), id));
	}

	@PatchMapping("/{id}/available-qty")
	public ApiResponse<ProductDetailResponse> updateAvailableQty(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody ProductAvailableQtyUpdateRequest request) {
		return ApiResponse.of(
			SuccessCode.OK, productService.updateAvailableQty(CurrentUser.id(authentication), id, request));
	}
}
