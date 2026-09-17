package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.Ingredient;
import org.jspecify.annotations.Nullable;

public record IngredientTagResponse(Integer id, String name, @Nullable String category) {

	public static IngredientTagResponse from(Ingredient ingredient) {
		return new IngredientTagResponse(
				ingredient.getId(), ingredient.getName(), ingredient.getCategory());
	}
}
