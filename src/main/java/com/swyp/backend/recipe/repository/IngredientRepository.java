package com.swyp.backend.recipe.repository;

import com.swyp.backend.recipe.entity.Ingredient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {

	long countByIdIn(Collection<Integer> ids);

	@Query("""
			select i from Ingredient i
			where i.name like concat('%', :query, '%')
			order by length(i.name) asc, i.name asc
			""")
	List<Ingredient> searchByName(@Param("query") String query, Pageable pageable);

	List<Ingredient> findByIdIn(Collection<Integer> ids);

	Optional<Ingredient> findByNormKey(String normKey);
}
