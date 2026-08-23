package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;

public record RecipeSummaryResponse(
		Long id, String title, String category, String imageThumbUrl, int viewCount, int likeCount) {

	public static RecipeSummaryResponse from(Recipe recipe) {
		return new RecipeSummaryResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getCategory(),
				recipe.getImageThumbUrl(),
				recipe.getViewCount(),
				recipe.getLikeCount());
	}
}
