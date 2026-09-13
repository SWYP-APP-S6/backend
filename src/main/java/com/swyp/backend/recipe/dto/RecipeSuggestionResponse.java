package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeDifficulty;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record RecipeSuggestionResponse(
		Long id,
		String title,
		@Nullable String imageThumbUrl,
		@Nullable Short cookTimeMinutes,
		@Nullable RecipeDifficulty difficulty,
		List<String> ingredientNames) {

	public static RecipeSuggestionResponse of(Recipe recipe, List<String> ingredientNames) {
		return new RecipeSuggestionResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getImageThumbUrl(),
				recipe.getCookTimeMinutes(),
				recipe.getDifficulty(),
				ingredientNames);
	}
}
