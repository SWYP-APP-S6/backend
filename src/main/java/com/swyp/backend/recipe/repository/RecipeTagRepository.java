package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeTagRepository extends JpaRepository<RecipeTag, Long> {

	List<RecipeTag> findByRecipeIdOrderByIdAsc(Long recipeId);
}
