package com.swyp.backend.recipe.service;

import com.swyp.backend.recipe.dto.IngredientTagResponse;
import com.swyp.backend.recipe.function.RecipeFunction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngredientService {

	private static final int MAX_RECOMMENDED_TAGS = 5;

	private final RecipeFunction recipeFunction;
	private final IngredientTagRecommender recommender;
	private final IngredientCatalogService catalogService;

	@Transactional(readOnly = true)
	public List<IngredientTagResponse> searchIngredients(String query, int size) {
		return recipeFunction.searchIngredients(query, size).stream()
				.map(IngredientTagResponse::from)
				.toList();
	}

	public List<IngredientTagResponse> recommendTags(String productName) {
		String trimmed = productName == null ? "" : productName.strip();
		if (trimmed.isEmpty()) {
			return List.of();
		}
		List<String> names = recommender.recommendTagNames(trimmed, MAX_RECOMMENDED_TAGS);
		if (names.isEmpty()) {
			return List.of();
		}
		return catalogService.findOrCreateByNames(names).stream()
				.limit(MAX_RECOMMENDED_TAGS)
				.map(IngredientTagResponse::from)
				.toList();
	}
}
