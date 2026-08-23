package com.swyp.backend.recipe.entity;

/**
 * Persisted as the enum name via {@code @Enumerated(STRING)}; the {@code recipe_nutrition.basis}
 * CHECK constraint lists these exact names, so keep the two in sync.
 */
public enum NutritionBasis {
	PER_SERVING,
	PER_100G
}
