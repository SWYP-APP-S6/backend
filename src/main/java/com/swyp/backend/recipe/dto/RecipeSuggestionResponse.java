package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Recipe;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record RecipeSuggestionResponse(
		Long id,
		String title,
		@Nullable String imageThumbUrl,
		@Nullable Short cookTimeMinutes,
		List<String> ingredientNames) {

	public static RecipeSuggestionResponse of(Recipe recipe, List<String> ingredientNames) {
		return new RecipeSuggestionResponse(
				recipe.getId(),
				recipe.getTitle(),
				recipe.getImageThumbUrl(),
				recipe.getCookTimeMinutes(),
				ingredientNames);
	}
}
