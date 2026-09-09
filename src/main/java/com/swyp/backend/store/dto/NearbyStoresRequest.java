package com.swyp.backend.store.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record NearbyStoresRequest(
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal minLat,
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal maxLat,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal minLng,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal maxLng) {

	@AssertTrue(message = "지도 범위의 최솟값이 최댓값보다 클 수 없습니다.")
	public boolean isBoundsOrdered() {
		return minLat == null || maxLat == null || minLng == null || maxLng == null
				|| (minLat.compareTo(maxLat) <= 0 && minLng.compareTo(maxLng) <= 0);
	}

	public BigDecimal centerLat() {
		return minLat.add(maxLat).divide(BigDecimal.TWO);
	}

	public BigDecimal centerLng() {
		return minLng.add(maxLng).divide(BigDecimal.TWO);
	}
}
