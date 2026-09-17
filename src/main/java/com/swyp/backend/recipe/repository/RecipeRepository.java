package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.Recipe;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

	Optional<Recipe> findByIdAndPublishedTrue(Long id);

	Page<Recipe> findByPublishedTrue(Pageable pageable);

	Page<Recipe> findByPublishedTrueAndCategory(String category, Pageable pageable);

	@Query("select distinct r.category from Recipe r "
			+ "where r.published = true and r.category is not null order by r.category")
	List<String> findDistinctPublishedCategories();

	@Query(nativeQuery = true, value = """
			select r.id
			from recipes r
			join recipe_ingredients ri on ri.recipe_id = r.id
			join ingredients i on i.id = ri.ingredient_id
			where r.is_published and i.id in (:ingredientIds)
			group by r.id
			order by
				max(case when position(i.name in r.title) > 0 then 1 else 0 end) desc,
				(select count(*) from recipe_ingredients x where x.recipe_id = r.id) asc,
				r.id asc
			limit :limit
			""")
	List<Long> findIdsMatchingIngredients(
			@Param("ingredientIds") Collection<Integer> ingredientIds, @Param("limit") int limit);

	@Query("select distinct r from Recipe r where r.id in :ids")
	List<Recipe> findAllByIdIn(@Param("ids") Collection<Long> ids);
}
