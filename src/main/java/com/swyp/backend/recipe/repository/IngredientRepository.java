package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.Ingredient;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {

	long countByIdInAndTagTrue(Collection<Integer> ids);

	@Query("""
			select t from Ingredient t
			where t.tag = true
				and t.id in (
					select coalesce(i.canonicalId, i.id) from Ingredient i
					where i.name like concat('%', :query, '%') escape '\\')
			order by length(t.name) asc, t.name asc
			""")
	List<Ingredient> searchTagsByName(@Param("query") String query, Pageable pageable);

	List<Ingredient> findByTagTrueOrderByNameAsc();

	List<Ingredient> findByIdIn(Collection<Integer> ids);

	List<Ingredient> findByIdInAndTagTrue(Collection<Integer> ids);

	List<Ingredient> findByNormKeyIn(Collection<String> normKeys);
}
