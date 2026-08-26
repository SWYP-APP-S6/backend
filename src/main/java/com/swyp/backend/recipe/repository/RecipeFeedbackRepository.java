package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeFeedbackRepository extends JpaRepository<RecipeFeedback, Long> {

	Optional<RecipeFeedback> findByUserIdAndRecipeId(Long userId, Long recipeId);
}
