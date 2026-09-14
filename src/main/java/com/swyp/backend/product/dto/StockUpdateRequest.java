package com.swyp.backend.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockUpdateRequest(
		@NotNull @Min(0) @Max(9999) Integer stockQty,
		@NotNull Boolean cancelOverflow) {
}
