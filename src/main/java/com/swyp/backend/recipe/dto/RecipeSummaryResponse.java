package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import org.jspecify.annotations.Nullable;

public record RecipeSummaryResponse(
		Long id,
		String title,
		@Nullable String category,
		@Nullable Short cookTimeMinutes,
		@Nullable String imageThumbUrl,
		int viewCount,
		int likeCount) {

	public static RecipeSummaryResponse from(Recipe recipe) {
		return new RecipeSummaryResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getCategory(),
				recipe.getCookTimeMinutes(),
				recipe.getImageThumbUrl(),
				recipe.getViewCount(),
				recipe.getLikeCount());
	}
}
