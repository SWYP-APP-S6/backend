package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.ProductCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record NearbyProductsRequest(
		@NotNull @Digits(integer = 3, fraction = 6) @DecimalMin("-90.0") @DecimalMax("90.0")
		BigDecimal lat,
		@NotNull @Digits(integer = 3, fraction = 6) @DecimalMin("-180.0") @DecimalMax("180.0")
		BigDecimal lng,
		ProductCategory category,
		NearbyProductSort sort,
		@Min(0) Integer page,
		@Min(1) @Max(100) Integer size) {

	private static final int DEFAULT_SIZE = 20;

	public NearbyProductsRequest {
		sort = sort == null ? NearbyProductSort.DISTANCE : sort;
		page = page == null ? 0 : page;
		size = size == null ? DEFAULT_SIZE : size;
	}
}
