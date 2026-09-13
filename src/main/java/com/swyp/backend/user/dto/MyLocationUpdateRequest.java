package com.swyp.backend.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MyLocationUpdateRequest(
		@NotBlank @Size(max = 100) String regionName,
		@NotNull @Digits(integer = 3, fraction = 6) @DecimalMin("-90.0") @DecimalMax("90.0")
		BigDecimal latitude,
		@NotNull @Digits(integer = 3, fraction = 6) @DecimalMin("-180.0") @DecimalMax("180.0")
		BigDecimal longitude) {}
