package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.ProductCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record ProductRegisterRequest(
		@NotBlank @Size(max = 30) String name,
		ProductCategory category,
		@Min(1) int initialQty,
		@Positive int originalPrice,
		@Positive int salePrice,
		@NotBlank String photoUrl,
		@Size(max = 5) List<Integer> ingredientTags,
		LocalDateTime pickupEndAt) {
}
