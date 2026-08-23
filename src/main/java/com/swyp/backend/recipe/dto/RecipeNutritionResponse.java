package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeNutrition;
import java.math.BigDecimal;

public record RecipeNutritionResponse(
		String basis,
		BigDecimal servingWeightG,
		BigDecimal calories,
		BigDecimal carbsG,
		BigDecimal proteinG,
		BigDecimal fatG,
		BigDecimal sodiumMg) {

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
