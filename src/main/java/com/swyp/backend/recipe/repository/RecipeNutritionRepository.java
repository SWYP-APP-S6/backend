package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeNutrition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeNutritionRepository extends JpaRepository<RecipeNutrition, Long> {
}
