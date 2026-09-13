package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeDifficulty;
import org.jspecify.annotations.Nullable;

public record RecipeSummaryResponse(
		Long id,
		String title,
		@Nullable String category,
		@Nullable Short cookTimeMinutes,
		@Nullable RecipeDifficulty difficulty,
		@Nullable String imageThumbUrl,
		int viewCount,
		int likeCount) {

	public static RecipeSummaryResponse from(Recipe recipe) {
		return new RecipeSummaryResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getCategory(),
				recipe.getCookTimeMinutes(),
				recipe.getDifficulty(),
				recipe.getImageThumbUrl(),
				recipe.getViewCount(),
				recipe.getLikeCount());
	}
}
