package com.swyp.backend.recipe.dto;

import com.swyp.backend.recipe.entity.RecipeStep;

public record RecipeStepResponse(short seq, String content, String imageUrl) {

	public static RecipeStepResponse from(RecipeStep step) {
		return new RecipeStepResponse(step.getSeq(), step.getContent(), step.getImageUrl());
	}
}
