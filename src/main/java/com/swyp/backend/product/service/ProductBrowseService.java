package com.swyp.backend.product.service;

import com.swyp.backend.common.BrowseProperties;
import com.swyp.backend.common.Distance;
import com.swyp.backend.hold.dto.HoldRef;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.product.dto.NearbyProductSort;
import com.swyp.backend.product.dto.NearbyProductsRequest;
import com.swyp.backend.product.dto.NearbyProductsResponse;
import com.swyp.backend.product.dto.NearbyStoreGroupResponse;
import com.swyp.backend.product.dto.ProductBrowseDetailResponse;
import com.swyp.backend.product.dto.ProductDetailRequest;
import com.swyp.backend.product.dto.SellableStoreGroup;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.recipe.dto.RecipeSuggestionResponse;
import com.swyp.backend.recipe.entity.Recipe;
import com.swyp.backend.recipe.function.RecipeFunction;
import com.swyp.backend.store.entity.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductBrowseService {

	private static final int MAX_SUGGESTED_RECIPES = 3;

	private final ProductFunction productFunction;
	private final RecipeFunction recipeFunction;
	private final HoldFunction holdFunction;
	private final BrowseProperties browseProperties;
	private final Clock clock;

	public NearbyProductsResponse findNearby(NearbyProductsRequest request) {
		List<SellableStoreGroup> nearby = productFunction.findSellableGroupedByStore(
				request.lat(),
				request.lng(),
				radiusOf(request),
				request.category());

		List<NearbyStoreGroupResponse> groups = nearby.stream()
				.sorted(comparatorFor(request.sort()))
				.map(NearbyStoreGroupResponse::from)
				.toList();

		return NearbyProductsResponse.of(groups, request.page(), request.size());
	}

	private int radiusOf(NearbyProductsRequest request) {
		return request.radiusMeters() == null
				? browseProperties.nearbyRadiusMeters()
				: request.radiusMeters();
	}

	public ProductBrowseDetailResponse getProductDetail(
			Long productId, ProductDetailRequest request, Long viewerId) {
		Product product = productFunction.getBrowsableById(productId);
		Store store = product.getStore();

		Integer distanceMeters = null;
		if (request.hasPosition()) {
			distanceMeters = (int) Math.round(Distance.metersBetween(
					request.lat().doubleValue(), request.lng().doubleValue(),
					store.getLatitude().doubleValue(), store.getLongitude().doubleValue()));
		}

		Optional<HoldRef> activeHold = holdFunction.findHoldingRefOf(viewerId)
				.filter(ref -> ref.expiresAt().isAfter(Instant.now(clock)));
		boolean holdsThisStore = activeHold
				.filter(ref -> ref.storeId().equals(store.getId()))
				.isPresent();

		return ProductBrowseDetailResponse.of(
				product,
				recipeFunction.ingredientNamesOf(product.getIngredientIds()),
				holdsThisStore ? holdFunction.findHoldingIdOf(viewerId, productId).orElse(null) : null,
				activeHold.isPresent() && !holdsThisStore,
				distanceMeters,
				store.isOpenAt(ZonedDateTime.now(clock)),
				product.getPickupEndAt().isAfter(LocalDateTime.now(clock)),
				suggestedRecipes(product));
	}

	private List<RecipeSuggestionResponse> suggestedRecipes(Product product) {
		List<Recipe> recipes = recipeFunction.findMatchingIngredients(
				product.getIngredientIds(), MAX_SUGGESTED_RECIPES);
		Map<Long, List<String>> namesByRecipe = recipeFunction.ingredientNamesByRecipeId(
				recipes.stream().map(Recipe::getId).toList());
		return recipes.stream()
				.map(recipe -> RecipeSuggestionResponse.of(
						recipe, namesByRecipe.getOrDefault(recipe.getId(), List.of())))
				.toList();
	}

	private static Comparator<SellableStoreGroup> comparatorFor(NearbyProductSort sort) {
		return switch (sort) {
			case DISTANCE -> Comparator.comparingInt(SellableStoreGroup::distanceMeters)
					.thenComparing(group -> group.store().getId());
			case PICKUP_DEADLINE -> Comparator
					.comparing(SellableStoreGroup::earliestPickupEndAt)
					.thenComparing(group -> group.store().getId());
		};
	}
}
