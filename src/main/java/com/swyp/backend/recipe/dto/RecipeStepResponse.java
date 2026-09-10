package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeStep;

import org.jspecify.annotations.Nullable;

public record RecipeStepResponse(short seq, String content, @Nullable String imageUrl) {

	public static RecipeStepResponse from(RecipeStep step) {
		return new RecipeStepResponse(step.getSeq(), step.getContent(), step.getImageUrl());
	}
}
