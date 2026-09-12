package com.swyp.backend.product.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.dto.HoldDisposition;
import com.swyp.backend.product.dto.ProductAvailableQtyUpdateRequest;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.entity.ProductStatus;
import com.swyp.backend.product.exception.ProductErrorCode;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.recipe.function.RecipeFunction;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.function.StoreFunction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private static final String OWNER_CANCEL_REASON = "점주가 판매를 종료해 찜이 취소됐어요.";
	private static final int MAX_PICKUP_WINDOW_HOURS = 24;

	private final ProductFunction productFunction;
	private final HoldFunction holdFunction;
	private final StoreFunction storeFunction;
	private final NotificationFunction notificationFunction;
	private final RecipeFunction recipeFunction;
	private final Clock clock;

	@Transactional
	public ProductDetailResponse registerProduct(Long ownerId, ProductRegisterRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		if (request.salePrice() >= request.originalPrice()) {
			throw new BusinessException(ProductErrorCode.INVALID_PRICE);
		}

		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime pickupEndAt = request.pickupEndAt() != null
				? request.pickupEndAt()
				: defaultPickupEndAt(store, now);
		if (!pickupEndAt.isAfter(now) || pickupEndAt.isAfter(now.plusHours(MAX_PICKUP_WINDOW_HOURS))) {
			throw new BusinessException(ProductErrorCode.INVALID_PICKUP_WINDOW);
		}
		if (request.ingredientTags() != null && !recipeFunction.allIngredientsExist(request.ingredientTags())) {
			throw new BusinessException(ProductErrorCode.INGREDIENT_NOT_FOUND);
		}

		Product product = new Product(
				store,
				request.name(),
				request.category() != null ? request.category() : ProductCategory.ETC,
				request.initialQty(),
				request.originalPrice(),
				request.salePrice(),
				now,
				pickupEndAt,
				request.photoUrl());
		if (request.ingredientTags() != null) {
			product.replaceIngredientIds(request.ingredientTags());
		}
		productFunction.save(product);
		return ProductDetailResponse.from(product, 0L);
	}

	public ProductDetailResponse getMyProduct(Long ownerId, Long productId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdAndStoreId(productId, store.getId());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	@Transactional
	public ProductDetailResponse updateAvailableQty(
			Long ownerId, Long productId, ProductAvailableQtyUpdateRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Map<Long, Product> locked = lockProducts(productId, request.availableQty() == 0);
		Product product = locked.get(productId);
		if (!product.getStore().getId().equals(store.getId())) {
			throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
		}
		if (product.getStatus() == ProductStatus.CLOSED) {
			throw new BusinessException(ProductErrorCode.PRODUCT_CLOSED);
		}

		if (request.availableQty() == 0) {
			applyZeroQtyDisposition(product, request.disposition(), locked);
		}
		product.adjustAvailableQty(request.availableQty());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	private Map<Long, Product> lockProducts(Long productId, boolean withHoldSiblings) {
		List<Long> ids = new ArrayList<>(List.of(productId));
		if (withHoldSiblings) {
			ids.addAll(holdFunction.findProductIdsSharingActiveHoldsWith(productId));
		}
		Map<Long, Product> locked = new LinkedHashMap<>();
		ids.stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));
		return locked;
	}

	private void applyZeroQtyDisposition(
			Product product, HoldDisposition disposition, Map<Long, Product> locked) {
		List<Hold> activeHolds = holdFunction.findActiveHoldsOfProduct(product.getId());
		if (activeHolds.isEmpty()) {
			return;
		}
		if (disposition == null) {
			throw new BusinessException(ProductErrorCode.DISPOSITION_REQUIRED);
		}
		if (disposition == HoldDisposition.CANCEL_ALL) {
			Instant now = Instant.now(clock);
			for (Hold hold : activeHolds) {
				hold.cancelByOwner(now, OWNER_CANCEL_REASON);
				for (HoldItem item : hold.getItems()) {
					locked.get(item.getProduct().getId()).releaseHold(item.getQty());
				}
				notificationFunction.notify(
						hold.getUser(),
						NotificationType.HOLD_CANCELED_BY_OWNER,
						"찜이 취소됐어요",
						product.getName() + " 판매가 종료되어 찜이 취소됐어요.",
						null);
			}
		}
	}

	private long completedQtyOf(Long productId) {
		return holdFunction.completedQtyOfProduct(productId);
	}


	private static LocalDateTime defaultPickupEndAt(Store store, LocalDateTime now) {
		LocalDateTime pickupEndAt = LocalDateTime.of(now.toLocalDate(), store.getBusinessCloseTime());
		return pickupEndAt.isAfter(now) ? pickupEndAt : pickupEndAt.plusDays(1);
	}
}
