package com.swyp.backend.recipe.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.JpaAuditingConfig;
import com.swyp.backend.recipe.entity.EstimateSource;
import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.entity.RecipeDifficulty;
import com.swyp.backend.recipe.entity.RecipeIngredient;
import com.swyp.backend.recipe.entity.RecipeStep;
import com.swyp.backend.recipe.entity.RecipeTag;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class RecipeRepositoryTest {

	@Autowired
	TestEntityManager entityManager;

	@Autowired
	RecipeRepository recipeRepository;

	@Autowired
	RecipeStepRepository recipeStepRepository;

	@Autowired
	RecipeIngredientRepository recipeIngredientRepository;

	@Autowired
	RecipeTagRepository recipeTagRepository;

	@Test
	void findByIdAndPublishedTrue_hidesUnpublishedRecipes() {
		Recipe published = entityManager.persist(recipe("반찬", true));
		Recipe unpublished = entityManager.persist(recipe("반찬", false));
		entityManager.flush();

		assertThat(recipeRepository.findByIdAndPublishedTrue(published.getId())).isPresent();
		assertThat(recipeRepository.findByIdAndPublishedTrue(unpublished.getId())).isEmpty();
	}

	@Test
	void findByPublishedTrueAndCategory_filtersByCategoryAndPublishedOnly() {
		entityManager.persist(recipe("반찬", true));
		entityManager.persist(recipe("국", true));
		entityManager.persist(recipe("반찬", false));
		entityManager.flush();

		Page<Recipe> page = recipeRepository.findByPublishedTrueAndCategory("반찬", PageRequest.of(0, 10));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).getCategory()).isEqualTo("반찬");
	}

	@Test
	void findDistinctPublishedCategories_isSortedAndExcludesUnpublishedOnlyCategories() {
		entityManager.persist(recipe("반찬", true));
		entityManager.persist(recipe("국", true));
		entityManager.persist(recipe("후식", false));
		entityManager.flush();

		assertThat(recipeRepository.findDistinctPublishedCategories()).containsExactly("국", "반찬");
	}

	@Test
	void findByRecipeIdWithIngredient_loadsIngredientAssociationEagerly() {
		Recipe recipe = entityManager.persist(recipe("반찬", true));
		Ingredient garlic = entityManager.persist(new Ingredient("마늘", "garlic", "채소"));
		entityManager.persist(new RecipeIngredient(recipe, (short) 1, garlic, null,
				new BigDecimal("2.00"), "쪽", "다진 마늘 2쪽"));
		entityManager.persist(new RecipeIngredient(recipe, (short) 2, null, null, null, null, "소금 약간"));
		entityManager.flush();
		entityManager.clear();

		List<RecipeIngredient> ingredients =
				recipeIngredientRepository.findByRecipeIdWithIngredient(recipe.getId());

		assertThat(ingredients).hasSize(2);
		assertThat(ingredients.get(0).getIngredient().getName()).isEqualTo("마늘");
		assertThat(ingredients.get(1).getIngredient()).isNull();
	}

	@Test
	void findByRecipeIdOrderBySeqAsc_returnsStepsInOrder() {
		Recipe recipe = entityManager.persist(recipe("반찬", true));
		entityManager.persist(new RecipeStep(recipe, (short) 2, "끓인다", null));
		entityManager.persist(new RecipeStep(recipe, (short) 1, "썬다", null));
		entityManager.flush();

		List<RecipeStep> steps = recipeStepRepository.findByRecipeIdOrderBySeqAsc(recipe.getId());

		assertThat(steps).extracting(RecipeStep::getSeq).containsExactly((short) 1, (short) 2);
	}

	@Test
	void findByRecipeIdOrderByIdAsc_returnsTags() {
		Recipe recipe = entityManager.persist(recipe("반찬", true));
		entityManager.persist(new RecipeTag(recipe, "간단요리"));
		entityManager.persist(new RecipeTag(recipe, "저칼로리"));
		entityManager.flush();

		assertThat(recipeTagRepository.findByRecipeIdOrderByIdAsc(recipe.getId()))
				.extracting(RecipeTag::getTag)
				.containsExactly("간단요리", "저칼로리");
	}

	@Test
	void assignDifficulty_keepsValueAndSourceTogether() {
		Recipe recipe = recipe("반찬", true);
		recipe.assignDifficulty(RecipeDifficulty.EASY, EstimateSource.AI);
		recipe.assignCookTimeMinutes((short) 20, EstimateSource.HUMAN);
		entityManager.persist(recipe);
		entityManager.flush();
		entityManager.clear();

		Recipe found = recipeRepository.findById(recipe.getId()).orElseThrow();

		assertThat(found.getDifficulty()).isEqualTo(RecipeDifficulty.EASY);
		assertThat(found.getDifficultySource()).isEqualTo(EstimateSource.AI);
		assertThat(found.getCookTimeMinutes()).isEqualTo((short) 20);
		assertThat(found.getCookTimeSource()).isEqualTo(EstimateSource.HUMAN);
	}

	@Test
	void assignDifficulty_clearingTheValueAlsoClearsTheSource() {
		Recipe recipe = recipe("반찬", true);
		recipe.assignDifficulty(RecipeDifficulty.HARD, EstimateSource.AI);
		entityManager.persist(recipe);
		entityManager.flush();

		recipe.assignDifficulty(null, EstimateSource.AI);
		entityManager.flush();
		entityManager.clear();

		Recipe found = recipeRepository.findById(recipe.getId()).orElseThrow();

		assertThat(found.getDifficulty()).isNull();
		assertThat(found.getDifficultySource()).isNull();
	}

	@Test
	void difficultyWithoutSource_isRejectedByTheDatabase() {
		Recipe recipe = entityManager.persist(recipe("반찬", true));
		entityManager.flush();

		assertThatThrownBy(() -> {
			entityManager.getEntityManager()
					.createNativeQuery("update recipes set difficulty = 'EASY' where id = :id")
					.setParameter("id", recipe.getId())
					.executeUpdate();
			entityManager.flush();
		}).hasMessageContaining("chk_recipes_difficulty_source");
	}

	@Test
	void findIdsMatchingIngredients_gathersRecipesThatUseAnAliasUnderItsTag() {
		Ingredient egg = entityManager.persist(Ingredient.tag("달걀", "test-egg", "달걀·유제품"));
		Ingredient eggAlias = entityManager.persist(Ingredient.aliasOf(egg, "계란", "test-egg-alias"));
		Ingredient tofu = entityManager.persist(Ingredient.tag("두부", "test-tofu", "두부·콩"));
		Recipe withAlias = recipeUsing("달걀말이", eggAlias);
		Recipe withTag = recipeUsing("아침 한 그릇", egg);
		recipeUsing("두부조림", tofu);
		entityManager.flush();

		assertThat(recipeRepository.findIdsMatchingIngredients(List.of(egg.getId()), 10))
				.containsExactly(withAlias.getId(), withTag.getId());
		assertThat(recipeRepository.findIdsMatchingIngredients(List.of(eggAlias.getId()), 10))
				.containsExactly(withAlias.getId(), withTag.getId());
	}

	private Recipe recipeUsing(String title, Ingredient ingredient) {
		Recipe recipe = new Recipe("mfds", "seq-" + System.nanoTime(), title, "반찬", "끓이기",
				(short) 1, "kogl");
		recipe.publish();
		entityManager.persist(recipe);
		entityManager.persist(new RecipeIngredient(recipe, (short) 1, ingredient, null, null, null,
				ingredient.getName()));
		return recipe;
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
