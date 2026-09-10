package com.swyp.backend.product.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.product.dto.NearbyProductsRequest;
import com.swyp.backend.product.dto.NearbyProductsResponse;
import com.swyp.backend.product.service.ProductBrowseService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품 탐색")
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

	private final ProductBrowseService productBrowseService;

	@GetMapping("/nearby")
	public ApiResponse<NearbyProductsResponse> getNearbyProducts(
			@Valid @ParameterObject @ModelAttribute NearbyProductsRequest request) {
		return ApiResponse.of(SuccessCode.OK, productBrowseService.findNearby(request));
	}
}
