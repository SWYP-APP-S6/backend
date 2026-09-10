package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeIngredient;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record RecipeIngredientResponse(
		short seq,
		@Nullable String groupName,
		@Nullable String ingredientName,
		@Nullable BigDecimal amount,
		@Nullable String unit,
		String rawText) {

	public static RecipeIngredientResponse from(RecipeIngredient recipeIngredient) {
		String ingredientName = recipeIngredient.getIngredient() != null
				? recipeIngredient.getIngredient().getName()
				: null;
		return new RecipeIngredientResponse(
				recipeIngredient.getSeq(),
				recipeIngredient.getGroupName(),
				ingredientName,
				recipeIngredient.getAmount(),
				recipeIngredient.getUnit(),
				recipeIngredient.getRawText());
	}
}
