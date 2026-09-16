package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.RecipeFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeFeedbackRepository extends JpaRepository<RecipeFeedback, Long> {

	Optional<RecipeFeedback> findByUserIdAndRecipeId(Long userId, Long recipeId);

	@Modifying
	@Query("delete from RecipeFeedback f where f.user.id = :userId")
	int deleteByUserId(@Param("userId") Long userId);
}
