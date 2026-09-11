package com.swyp.backend.recipe.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.recipe.entity.Ingredient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Set;
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

	public boolean allIngredientsExist(Collection<Integer> ingredientIds) {
		Set<Integer> distinctIds = Set.copyOf(ingredientIds);
		if (distinctIds.isEmpty()) {
			return true;
		}
		return ingredientRepository.countByIdIn(distinctIds) == distinctIds.size();
	}

	public List<String> ingredientNamesOf(Collection<Integer> ingredientIds) {
		if (ingredientIds.isEmpty()) {
			return List.of();
		}
		return ingredientRepository.findAllById(ingredientIds).stream()
				.map(Ingredient::getName)
				.toList();
	}

	public List<Recipe> findMatchingIngredients(Collection<Integer> ingredientIds, int limit) {
		if (ingredientIds.isEmpty()) {
			return List.of();
		}
		List<Long> orderedIds = recipeRepository.findIdsMatchingIngredients(ingredientIds, limit);
		if (orderedIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Recipe> byId = recipeRepository.findAllByIdIn(orderedIds).stream()
				.collect(Collectors.toMap(Recipe::getId, recipe -> recipe));
		return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
	}

	public Map<Long, List<String>> ingredientNamesByRecipeId(Collection<Long> recipeIds) {
		if (recipeIds.isEmpty()) {
			return Map.of();
		}
		return recipeIngredientRepository.findByRecipeIdInWithIngredient(recipeIds).stream()
				.filter(ingredient -> ingredient.getIngredient() != null)
				.collect(Collectors.groupingBy(
						ingredient -> ingredient.getRecipe().getId(),
						LinkedHashMap::new,
						Collectors.mapping(
								ingredient -> ingredient.getIngredient().getName(), Collectors.toList())));
	}

	public List<RecipeTag> findTags(Long recipeId) {
		return recipeTagRepository.findByRecipeIdOrderByIdAsc(recipeId);
	}
}
