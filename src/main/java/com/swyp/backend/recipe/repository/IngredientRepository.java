package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.Ingredient;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {

	long countByIdIn(Collection<Integer> ids);
}
