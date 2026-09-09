package com.swyp.backend.recipe.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeIngredient;
import com.swyp.backend.recipe.entity.RecipeNutrition;
import com.swyp.backend.recipe.entity.RecipeStep;
import com.swyp.backend.recipe.entity.RecipeTag;
import com.swyp.backend.recipe.exception.RecipeErrorCode;
import com.swyp.backend.recipe.repository.IngredientRepository;
import com.swyp.backend.recipe.repository.RecipeIngredientRepository;
import com.swyp.backend.recipe.repository.RecipeNutritionRepository;
import com.swyp.backend.recipe.repository.RecipeRepository;
import com.swyp.backend.recipe.repository.RecipeStepRepository;
import com.swyp.backend.recipe.repository.RecipeTagRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RecipeFunction {

	private final RecipeRepository recipeRepository;
	private final RecipeStepRepository recipeStepRepository;
	private final IngredientRepository ingredientRepository;
	private final RecipeIngredientRepository recipeIngredientRepository;
	private final RecipeNutritionRepository recipeNutritionRepository;
	private final RecipeTagRepository recipeTagRepository;

	public Recipe getPublishedById(Long id) {
		return recipeRepository.findByIdAndPublishedTrue(id)
				.orElseThrow(() -> new BusinessException(RecipeErrorCode.RECIPE_NOT_FOUND));
	}

	public Page<Recipe> findPublishedByCategory(String category, Pageable pageable) {
		return StringUtils.hasText(category)
				? recipeRepository.findByPublishedTrueAndCategory(category, pageable)
				: recipeRepository.findByPublishedTrue(pageable);
	}

	public List<String> findPublishedCategories() {
		return recipeRepository.findDistinctPublishedCategories();
	}

	public List<RecipeStep> findSteps(Long recipeId) {
		return recipeStepRepository.findByRecipeIdOrderBySeqAsc(recipeId);
	}

	public List<RecipeIngredient> findIngredients(Long recipeId) {
		return recipeIngredientRepository.findByRecipeIdWithIngredient(recipeId);
	}

	public Optional<RecipeNutrition> findNutrition(Long recipeId) {
		return recipeNutritionRepository.findById(recipeId);
	}

	public long countIngredientsByIds(Collection<Integer> ingredientIds) {
		return ingredientRepository.countByIdIn(ingredientIds);
	}

	public List<RecipeTag> findTags(Long recipeId) {
		return recipeTagRepository.findByRecipeIdOrderByIdAsc(recipeId);
	}
}
