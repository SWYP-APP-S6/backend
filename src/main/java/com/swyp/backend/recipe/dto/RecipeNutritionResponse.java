package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeNutrition;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record RecipeNutritionResponse(
		String basis,
		@Nullable BigDecimal servingWeightG,
		@Nullable BigDecimal calories,
		@Nullable BigDecimal carbsG,
		@Nullable BigDecimal proteinG,
		@Nullable BigDecimal fatG,
		@Nullable BigDecimal sodiumMg) {

	public static RecipeNutritionResponse from(RecipeNutrition nutrition) {
		return new RecipeNutritionResponse(
				nutrition.getBasis().name(),
				nutrition.getServingWeightG(),
				nutrition.getCalories(),
				nutrition.getCarbsG(),
				nutrition.getProteinG(),
				nutrition.getFatG(),
				nutrition.getSodiumMg());
	}
}
