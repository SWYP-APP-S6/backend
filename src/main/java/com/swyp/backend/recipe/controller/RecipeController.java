package com.swyp.backend.recipe.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.recipe.dto.RecipeDetailResponse;
import com.swyp.backend.recipe.dto.RecipeSummaryResponse;
import com.swyp.backend.recipe.service.RecipeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recipes")
public class RecipeController {

	private final RecipeService recipeService;

	@GetMapping("/{id}")
	public ApiResponse<RecipeDetailResponse> getRecipe(@PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, recipeService.getRecipe(id));
	}

	@GetMapping("/categories")
	public ApiResponse<List<String>> getCategories() {
		return ApiResponse.of(SuccessCode.OK, recipeService.getCategories());
	}

	@GetMapping
	public ApiResponse<PageResponse<RecipeSummaryResponse>> getRecipes(
			@RequestParam(required = false) String category,
			@PageableDefault(size = 20, sort = "viewCount", direction = Sort.Direction.DESC) Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, recipeService.getRecipes(category, pageable));
	}
}
