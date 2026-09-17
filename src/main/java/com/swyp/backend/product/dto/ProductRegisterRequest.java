package com.swyp.backend.product.dto;

import com.swyp.backend.product.entity.ProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
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
		@NotBlank @Size(max = 512) String photoUrl,
		@Size(max = 5) List<Integer> ingredientTags,
		@Schema(
				description = "픽업 종료 시각. 한국 시간의 벽시계 값이다."
						+ " 오프셋을 붙여 보내면(+09:00, Z) 서버가 한국 시간으로 바꿔 받는다."
						+ " 비우면 가게 영업 종료 시각으로 채운다.",
				example = "2026-09-17T22:00:00")
		LocalDateTime pickupEndAt) {
}
