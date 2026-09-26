package com.swyp.backend.admin.controller;

import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.product.dto.AdminProductFilter;
import com.swyp.backend.product.dto.AdminProductResponse;
import com.swyp.backend.product.service.AdminProductService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminProduct", description = "관리자 상품 조회·노출 진단")
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

	private final AdminProductService adminProductService;

	@GetMapping
	@PageQueryParams
	public ApiResponse<PageResponse<AdminProductResponse>> getAdminProducts(
			@RequestParam(required = false) Long storeId,
			@RequestParam(defaultValue = "ALL") AdminProductFilter filter,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.of(
				SuccessCode.OK, adminProductService.getProducts(storeId, filter, pageable));
	}
}
