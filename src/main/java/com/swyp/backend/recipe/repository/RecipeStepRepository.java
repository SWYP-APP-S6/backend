package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

	List<RecipeStep> findByRecipeIdOrderBySeqAsc(Long recipeId);
}
