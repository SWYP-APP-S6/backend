package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeIngredient;
import java.math.BigDecimal;

/** {@code ingredientName} is null when ingestion couldn't match a master ingredient — {@code rawText}
 *  is always populated so the line still renders. */
public record RecipeIngredientResponse(
		short seq, String groupName, String ingredientName, BigDecimal amount, String unit, String rawText) {

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
