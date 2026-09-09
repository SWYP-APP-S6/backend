package com.swyp.backend.store.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record StoreProductsRequest(
		@Digits(integer = 3, fraction = 6) @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
		@Digits(integer = 3, fraction = 6) @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng) {

	@AssertTrue(message = "위도와 경도는 함께 보내야 합니다.")
	public boolean isPositionComplete() {
		return (lat == null) == (lng == null);
	}

	public boolean hasPosition() {
		return lat != null && lng != null;
	}
}
