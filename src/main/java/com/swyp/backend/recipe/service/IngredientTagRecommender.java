package com.swyp.backend.recipe.service;

import java.util.List;

public interface IngredientTagRecommender {

	List<String> recommendTagNames(String productName, int limit);
}
