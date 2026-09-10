package com.swyp.backend.recipe.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.recipe.dto.RecipeDetailResponse;
import com.swyp.backend.recipe.dto.RecipeIngredientResponse;
import com.swyp.backend.recipe.dto.RecipeNutritionResponse;
import com.swyp.backend.recipe.dto.RecipeStepResponse;
import com.swyp.backend.recipe.dto.RecipeSummaryResponse;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeTag;
import com.swyp.backend.recipe.function.RecipeFunction;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

	private final RecipeFunction recipeFunction;

	public RecipeDetailResponse getRecipe(Long id) {
		Recipe recipe = recipeFunction.getPublishedById(id);

		List<RecipeStepResponse> steps = recipeFunction.findSteps(id).stream()
				.map(RecipeStepResponse::from)
				.toList();
		List<RecipeIngredientResponse> ingredients =
				recipeFunction.findIngredients(id).stream()
						.map(RecipeIngredientResponse::from)
						.toList();
		RecipeNutritionResponse nutrition = recipeFunction.findNutrition(id)
				.map(RecipeNutritionResponse::from)
				.orElse(null);
		List<String> tags = recipeFunction.findTags(id).stream()
				.map(RecipeTag::getTag)
				.toList();

		return RecipeDetailResponse.of(recipe, steps, ingredients, nutrition, tags);
	}

	public List<String> getCategories() {
		return recipeFunction.findPublishedCategories();
	}

	public PageResponse<RecipeSummaryResponse> getRecipes(String category, Pageable pageable) {
		Page<Recipe> page = recipeFunction.findPublishedByCategory(category, pageable);
		List<RecipeSummaryResponse> content =
				page.getContent().stream().map(RecipeSummaryResponse::from).toList();
		return PageResponse.of(content, page);
	}
}
