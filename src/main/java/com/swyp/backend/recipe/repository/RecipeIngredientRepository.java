package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeIngredient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

	@Query("select ri from RecipeIngredient ri left join fetch ri.ingredient "
			+ "where ri.recipe.id = :recipeId order by ri.seq")
	List<RecipeIngredient> findByRecipeIdWithIngredient(@Param("recipeId") Long recipeId);
}
