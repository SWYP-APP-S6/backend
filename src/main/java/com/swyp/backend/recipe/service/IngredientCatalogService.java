package com.swyp.backend.recipe.service;

import com.swyp.backend.recipe.entity.Ingredient;
import com.swyp.backend.recipe.function.RecipeFunction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientCatalogService {

	private final RecipeFunction recipeFunction;

	@Transactional
	public List<Ingredient> findOrCreateByNames(List<String> names) {
		Set<String> normKeys = new LinkedHashSet<>();
		List<String> kept = new ArrayList<>();
		for (String name : names) {
			String trimmed = name == null ? "" : name.strip();
			String normKey = IngredientNames.normKeyOf(trimmed);
			if (!normKey.isEmpty() && normKeys.add(normKey)) {
				kept.add(trimmed);
			}
		}

		List<Ingredient> tags = new ArrayList<>();
		for (String name : kept) {
			tags.add(findOrCreate(name));
		}
		return tags;
	}

	private Ingredient findOrCreate(String name) {
		String normKey = IngredientNames.normKeyOf(name);
		Optional<Ingredient> existing = recipeFunction.findIngredientByNormKey(normKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		try {
			return recipeFunction.saveIngredient(new Ingredient(name, normKey, null));
		} catch (DataIntegrityViolationException wonTheRace) {
			return recipeFunction.findIngredientByNormKey(normKey).orElseThrow(() -> wonTheRace);
		}
	}
}
