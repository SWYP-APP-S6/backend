package com.swyp.backend.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Nutrition facts, 1:1 with {@link Recipe} (shared PK via {@code @MapsId}). {@code basis} is kept
 * even though every current source is per-serving — comparing a 35kcal porridge to a 170kcal dessert
 * on the same scale requires knowing which basis each row uses once a second source arrives.
 */
@Getter
@Entity
@Table(name = "recipe_nutrition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeNutrition {

	@Id
	@Column(name = "recipe_id")
	private Long recipeId;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "recipe_id")
	private Recipe recipe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private NutritionBasis basis;

	@Column(name = "serving_weight_g", precision = 8, scale = 2)
	private BigDecimal servingWeightG;

	@Column(precision = 8, scale = 2)
	private BigDecimal calories;

	@Column(name = "carbs_g", precision = 8, scale = 2)
	private BigDecimal carbsG;

	@Column(name = "protein_g", precision = 8, scale = 2)
	private BigDecimal proteinG;

	@Column(name = "fat_g", precision = 8, scale = 2)
	private BigDecimal fatG;

	@Column(name = "sodium_mg", precision = 8, scale = 2)
	private BigDecimal sodiumMg;

	public RecipeNutrition(
			Recipe recipe,
			NutritionBasis basis,
			BigDecimal servingWeightG,
			BigDecimal calories,
			BigDecimal carbsG,
			BigDecimal proteinG,
			BigDecimal fatG,
			BigDecimal sodiumMg) {
		this.recipe = recipe;
		this.basis = basis;
		this.servingWeightG = servingWeightG;
		this.calories = calories;
		this.carbsG = carbsG;
		this.proteinG = proteinG;
		this.fatG = fatG;
		this.sodiumMg = sodiumMg;
	}
}
