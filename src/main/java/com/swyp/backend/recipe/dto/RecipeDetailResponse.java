package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import java.util.List;
import com.swyp.backend.recipe.entity.RecipeDifficulty;
import org.jspecify.annotations.Nullable;

public record RecipeDetailResponse(
		Long id,
		String title,
		@Nullable String category,
		@Nullable String cookMethod,
		@Nullable Short cookTimeMinutes,
		@Nullable RecipeDifficulty difficulty,
		short servings,
		@Nullable String imageUrl,
		@Nullable String imageThumbUrl,
		@Nullable String sourceUrl,
		int viewCount,
		int likeCount,
		List<RecipeStepResponse> steps,
		List<RecipeIngredientResponse> ingredients,
		@Nullable RecipeNutritionResponse nutrition,
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
				recipe.getCookTimeMinutes(),
				recipe.getDifficulty(),
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
