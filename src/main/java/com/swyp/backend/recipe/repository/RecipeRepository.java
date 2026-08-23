package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.Recipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

	Optional<Recipe> findByIdAndPublishedTrue(Long id);

	Page<Recipe> findByPublishedTrue(Pageable pageable);

	Page<Recipe> findByPublishedTrueAndCategory(String category, Pageable pageable);

	@Query("select distinct r.category from Recipe r "
			+ "where r.published = true and r.category is not null order by r.category")
	List<String> findDistinctPublishedCategories();
}
