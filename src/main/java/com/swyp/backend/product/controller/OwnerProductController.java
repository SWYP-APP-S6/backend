package com.swyp.backend.product.controller;

import com.swyp.backend.common.openapi.ApiErrorCodes;
import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.product.dto.OwnerProductFilter;
import com.swyp.backend.product.dto.OwnerProductListResponse;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductPhotoResponse;
import com.swyp.backend.product.dto.ProductPreviewResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.dto.StockReconfirmRequest;
import com.swyp.backend.product.dto.StockUpdateRequest;
import com.swyp.backend.product.service.ProductService;
import com.swyp.backend.store.exception.StoreErrorCode;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OwnerProduct", description = "점주 상품")
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

	@PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ProductPhotoResponse> uploadProductPhoto(
			@AuthenticationPrincipal Long ownerId, @RequestPart("file") MultipartFile file) {
		return ApiResponse.of(SuccessCode.OK, productService.uploadPhoto(ownerId, file));
	}

	@PostMapping("/preview")
	public ApiResponse<ProductPreviewResponse> previewProduct(
			@AuthenticationPrincipal Long ownerId, @Valid @RequestBody ProductRegisterRequest request) {
		return ApiResponse.of(SuccessCode.OK, productService.previewProduct(ownerId, request));
	}

	@GetMapping
	@PageQueryParams
	@ApiErrorCodes(in = StoreErrorCode.class, codes = "STORE_NOT_REGISTERED")
	public ApiResponse<OwnerProductListResponse> getMyProducts(
			@AuthenticationPrincipal Long ownerId,
			@RequestParam(defaultValue = "ALL") OwnerProductFilter filter,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, productService.getMyProducts(ownerId, filter, pageable));
	}

	@GetMapping("/{id}")
	public ApiResponse<ProductDetailResponse> getMyProduct(
			@AuthenticationPrincipal Long ownerId, @PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, productService.getMyProduct(ownerId, id));
	}

	@PatchMapping("/{id}/stock")
	public ApiResponse<ProductDetailResponse> updateStock(
			@AuthenticationPrincipal Long ownerId,
			@PathVariable Long id,
			@Valid @RequestBody StockUpdateRequest request) {
		return ApiResponse.of(SuccessCode.OK, productService.updateStock(ownerId, id, request));
	}

	@PostMapping("/{id}/stock-reconfirm")
	public ApiResponse<ProductDetailResponse> answerStockReconfirm(
			@AuthenticationPrincipal Long ownerId,
			@PathVariable Long id,
			@Valid @RequestBody StockReconfirmRequest request) {
		return ApiResponse.of(
				SuccessCode.OK, productService.answerStockReconfirm(ownerId, id, request));
	}
}
