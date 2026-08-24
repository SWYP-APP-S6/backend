package com.swyp.backend.recipe.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.entity.NutritionBasis;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeIngredient;
import com.swyp.backend.recipe.entity.RecipeNutrition;
import com.swyp.backend.recipe.entity.RecipeStep;
import com.swyp.backend.recipe.entity.RecipeTag;
import com.swyp.backend.recipe.repository.IngredientRepository;
import com.swyp.backend.recipe.repository.RecipeIngredientRepository;
import com.swyp.backend.recipe.repository.RecipeNutritionRepository;
import com.swyp.backend.recipe.repository.RecipeRepository;
import com.swyp.backend.recipe.repository.RecipeStepRepository;
import com.swyp.backend.recipe.repository.RecipeTagRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecipeControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RecipeRepository recipeRepository;

	@Autowired
	RecipeStepRepository recipeStepRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	RecipeIngredientRepository recipeIngredientRepository;

	@Autowired
	RecipeNutritionRepository recipeNutritionRepository;

	@Autowired
	RecipeTagRepository recipeTagRepository;

	@Test
	void getRecipe_returnsFullDetail() throws Exception {
		Recipe recipe = recipeRepository.save(recipe("반찬", true));
		recipeStepRepository.save(new RecipeStep(recipe, (short) 1, "재료를 썬다", null));
		recipeStepRepository.save(new RecipeStep(recipe, (short) 2, "끓인다", null));
		Ingredient garlic = ingredientRepository.save(new Ingredient("마늘", "garlic-" + recipe.getId(), "채소"));
		recipeIngredientRepository.save(new RecipeIngredient(recipe, (short) 1, garlic, null,
				new BigDecimal("2.00"), "쪽", "다진 마늘 2쪽"));
		recipeNutritionRepository.save(new RecipeNutrition(recipe, NutritionBasis.PER_SERVING,
				new BigDecimal("200.00"), new BigDecimal("350.00"), new BigDecimal("40.00"),
				new BigDecimal("10.00"), new BigDecimal("12.00"), new BigDecimal("500.00")));
		recipeTagRepository.save(new RecipeTag(recipe, "간단요리"));

		mockMvc.perform(get("/recipes/{id}", recipe.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.title").value("제목"))
			.andExpect(jsonPath("$.data.steps.length()").value(2))
			.andExpect(jsonPath("$.data.steps[0].content").value("재료를 썬다"))
			.andExpect(jsonPath("$.data.ingredients[0].ingredientName").value("마늘"))
			.andExpect(jsonPath("$.data.nutrition.basis").value("PER_SERVING"))
			.andExpect(jsonPath("$.data.tags[0]").value("간단요리"));
	}

	@Test
	void getRecipe_unpublished_returnsNotFound() throws Exception {
		Recipe recipe = recipeRepository.save(recipe("반찬", false));

		mockMvc.perform(get("/recipes/{id}", recipe.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
	}

	@Test
	void getCategories_returnsDistinctSortedPublishedCategories() throws Exception {
		recipeRepository.save(recipe("반찬", true));
		recipeRepository.save(recipe("국", true));
		recipeRepository.save(recipe("후식", false));

		mockMvc.perform(get("/recipes/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0]").value("국"))
			.andExpect(jsonPath("$.data[1]").value("반찬"));
	}

	@Test
	void getRecipes_defaultsToPopularityOrder_andSupportsCategoryFilter() throws Exception {
		Recipe low = recipeRepository.save(recipe("반찬", true));
		low.increaseViewCount();
		Recipe high = recipeRepository.save(recipe("반찬", true));
		for (int i = 0; i < 5; i++) {
			high.increaseViewCount();
		}
		recipeRepository.save(recipe("국", true));

		mockMvc.perform(get("/recipes").param("category", "반찬"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(2))
			.andExpect(jsonPath("$.data.content[0].id").value(high.getId()))
			.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	private static Recipe recipe(String category, boolean published) {
		Recipe recipe = new Recipe("mfds", "seq-" + System.nanoTime(), "제목", category, "끓이기",
				(short) 1, "kogl");
		if (published) {
			recipe.publish();
		}
		return recipe;
	}
}
