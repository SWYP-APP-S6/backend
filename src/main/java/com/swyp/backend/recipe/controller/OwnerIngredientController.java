package com.swyp.backend.recipe.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.recipe.dto.IngredientTagResponse;
import com.swyp.backend.recipe.service.IngredientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OwnerIngredient", description = "점주 식자재 태그")
@RestController
@RequiredArgsConstructor
@RequestMapping("/owner/ingredients")
public class OwnerIngredientController {

	private final IngredientService ingredientService;

	@GetMapping
	public ApiResponse<List<IngredientTagResponse>> searchIngredients(
			@RequestParam String query,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.of(SuccessCode.OK, ingredientService.searchIngredients(query, size));
	}

	@GetMapping("/recommendations")
	public ApiResponse<List<IngredientTagResponse>> recommendIngredientTags(
			@RequestParam String name) {
		return ApiResponse.of(SuccessCode.OK, ingredientService.recommendTags(name));
	}
}
