package com.swyp.backend.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "recipe_ingredients",
		uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "seq"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@Column(nullable = false)
	private short seq;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ingredient_id")
	private Ingredient ingredient;

	@Column(name = "group_name", length = 64)
	private String groupName;

	@Column(precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(length = 16)
	private String unit;

	@Column(name = "raw_text", nullable = false, length = 255)
	private String rawText;

	public RecipeIngredient(
			Recipe recipe,
			short seq,
			Ingredient ingredient,
			String groupName,
			BigDecimal amount,
			String unit,
			String rawText) {
		this.recipe = recipe;
		this.seq = seq;
		this.ingredient = ingredient;
		this.groupName = groupName;
		this.amount = amount;
		this.unit = unit;
		this.rawText = rawText;
	}
}
