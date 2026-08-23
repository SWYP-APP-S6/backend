package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import java.util.List;

public record RecipeDetailResponse(
		Long id,
		String title,
		String category,
		String cookMethod,
		short servings,
		String imageUrl,
		String imageThumbUrl,
		String sourceUrl,
		int viewCount,
		int likeCount,
		List<RecipeStepResponse> steps,
		List<RecipeIngredientResponse> ingredients,
		RecipeNutritionResponse nutrition,
		List<String> tags) {

	public static RecipeDetailResponse of(
			Recipe recipe,
			List<RecipeStepResponse> steps,
			List<RecipeIngredientResponse> ingredients,
			RecipeNutritionResponse nutrition,
			List<String> tags) {
		return new RecipeDetailResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getCategory(),
				recipe.getCookMethod(),
				recipe.getServings(),
				recipe.getImageUrl(),
				recipe.getImageThumbUrl(),
				recipe.getSourceUrl(),
				recipe.getViewCount(),
				recipe.getLikeCount(),
				steps,
				ingredients,
				nutrition,
				tags);
	}
}
