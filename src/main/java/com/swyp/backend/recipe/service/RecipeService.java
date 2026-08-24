package com.swyp.backend.recipe.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.recipe.dto.RecipeDetailResponse;
import com.swyp.backend.recipe.dto.RecipeIngredientResponse;
import com.swyp.backend.recipe.dto.RecipeNutritionResponse;
import com.swyp.backend.recipe.dto.RecipeStepResponse;
import com.swyp.backend.recipe.dto.RecipeSummaryResponse;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeTag;
import com.swyp.backend.recipe.exception.RecipeErrorCode;
import com.swyp.backend.recipe.repository.RecipeIngredientRepository;
import com.swyp.backend.recipe.repository.RecipeNutritionRepository;
import com.swyp.backend.recipe.repository.RecipeRepository;
import com.swyp.backend.recipe.repository.RecipeStepRepository;
import com.swyp.backend.recipe.repository.RecipeTagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

	private final RecipeRepository recipeRepository;
	private final RecipeStepRepository recipeStepRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeNutritionRepository recipeNutritionRepository;
	private final RecipeTagRepository recipeTagRepository;

	public RecipeDetailResponse getRecipe(Long id) {
		Recipe recipe = validateAndGetRecipe(id);

		List<RecipeStepResponse> steps = recipeStepRepository.findByRecipeIdOrderBySeqAsc(id).stream()
				.map(RecipeStepResponse::from)
				.toList();
		List<RecipeIngredientResponse> ingredients =
				recipeIngredientRepository.findByRecipeIdWithIngredient(id).stream()
						.map(RecipeIngredientResponse::from)
						.toList();
		RecipeNutritionResponse nutrition = recipeNutritionRepository.findById(id)
				.map(RecipeNutritionResponse::from)
				.orElse(null);
		List<String> tags = recipeTagRepository.findByRecipeIdOrderByIdAsc(id).stream()
				.map(RecipeTag::getTag)
				.toList();

		return RecipeDetailResponse.of(recipe, steps, ingredients, nutrition, tags);
	}

	public List<String> getCategories() {
		return recipeRepository.findDistinctPublishedCategories();
	}

	public PageResponse<RecipeSummaryResponse> getRecipes(String category, Pageable pageable) {
		Page<Recipe> page = StringUtils.hasText(category)
				? recipeRepository.findByPublishedTrueAndCategory(category, pageable)
				: recipeRepository.findByPublishedTrue(pageable);
		List<RecipeSummaryResponse> content =
				page.getContent().stream().map(RecipeSummaryResponse::from).toList();
		return PageResponse.of(content, page);
	}

	private Recipe validateAndGetRecipe(Long id) {
		return recipeRepository.findByIdAndPublishedTrue(id)
				.orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
	}
}
