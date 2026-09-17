package com.swyp.backend.recipe.service;

import com.swyp.backend.recipe.dto.IngredientTagResponse;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.function.RecipeFunction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngredientService {

	private static final int MAX_RECOMMENDED_TAGS = 5;
	private static final int MAX_PRODUCT_NAME_LENGTH = 30;

	private final RecipeFunction recipeFunction;
	private final IngredientTagRecommender recommender;

	@Transactional(readOnly = true)
	public List<IngredientTagResponse> searchIngredients(String query, int size) {
		return recipeFunction.searchTags(query, size).stream()
				.map(IngredientTagResponse::from)
				.toList();
	}

	public List<IngredientTagResponse> recommendTags(String productName) {
		String trimmed = productName == null ? "" : productName.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_PRODUCT_NAME_LENGTH) {
			return List.of();
		}
		List<String> tagNames = recipeFunction.findAllTags().stream()
				.map(Ingredient::getName)
				.toList();
		if (tagNames.isEmpty()) {
			return List.of();
		}
		List<String> normKeys = recommender.recommendTagNames(trimmed, tagNames, MAX_RECOMMENDED_TAGS)
				.stream()
				.map(IngredientNames::normKeyOf)
				.filter(normKey -> !normKey.isEmpty())
				.distinct()
				.toList();
		return recipeFunction.findTagsByNormKeys(normKeys).stream()
				.limit(MAX_RECOMMENDED_TAGS)
				.map(IngredientTagResponse::from)
				.toList();
	}
}
