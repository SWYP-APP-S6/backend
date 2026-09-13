package com.swyp.backend.product.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductPreviewResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.dto.StockReconfirmRequest;
import com.swyp.backend.product.dto.StockUpdateRequest;
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

	private static final String OWNER_SHORTAGE_REASON = "매장 재고가 모자라 찜이 취소됐어요.";
	private static final int MAX_PICKUP_WINDOW_HOURS = 24;

	private final ProductFunction productFunction;
	private final HoldFunction holdFunction;
	private final StoreFunction storeFunction;
	private final NotificationFunction notificationFunction;
	private final RecipeFunction recipeFunction;
	private final Clock clock;

	@Transactional
	public ProductDetailResponse registerProduct(Long ownerId, ProductRegisterRequest request) {
		Product product = buildProduct(ownerId, request);
		productFunction.save(product);
		return ProductDetailResponse.from(product, 0L);
	}

	public ProductPreviewResponse previewProduct(Long ownerId, ProductRegisterRequest request) {
		return ProductPreviewResponse.from(buildProduct(ownerId, request));
	}

	private Product buildProduct(Long ownerId, ProductRegisterRequest request) {
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
		return product;
	}

	public ProductDetailResponse getMyProduct(Long ownerId, Long productId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdAndStoreId(productId, store.getId());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	@Transactional
	public ProductDetailResponse updateStock(
			Long ownerId, Long productId, StockUpdateRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdForUpdate(productId);
		if (!product.getStore().getId().equals(store.getId())) {
			throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
		}
		if (product.getStatus() == ProductStatus.CLOSED) {
			throw new BusinessException(ProductErrorCode.PRODUCT_CLOSED);
		}
		if (product.isStockLocked()) {
			throw new BusinessException(ProductErrorCode.STOCK_LOCKED);
		}
		if (request.stockQty() < product.minAdjustableQty()) {
			throw new BusinessException(ProductErrorCode.QTY_BELOW_MINIMUM);
		}

		if (request.cancelOverflow()) {
			cancelOverflowHolds(product, request.stockQty());
		}
		product.restock(request.stockQty());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	@Transactional
	public ProductDetailResponse answerStockReconfirm(
			Long ownerId, Long productId, StockReconfirmRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdForUpdate(productId);
		if (!product.getStore().getId().equals(store.getId())) {
			throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
		}
		if (product.getReconfirmSentAt() == null) {
			throw new BusinessException(ProductErrorCode.RECONFIRM_NOT_REQUESTED);
		}

		Instant now = Instant.now(clock);
		if (request.confirmed()) {
			product.confirmStock(now);
		} else {
			product.denyStockConfirmation(now);
		}
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	// 먼저 찜한 손님부터 재고를 배정하고, 배정받지 못한 찜을 취소한다. 뒤에 찜한 사람이
	// 앞사람의 몫을 빼앗지 않게 하는 것이 선착순의 뜻이다.
	private void cancelOverflowHolds(Product product, int stockQty) {
		int remaining = stockQty;
		for (Hold hold : holdFunction.findActiveHoldsOfProduct(product.getId())) {
			if (hold.getQty() <= remaining) {
				remaining -= hold.getQty();
				continue;
			}
			hold.cancelByOwner(Instant.now(clock), OWNER_SHORTAGE_REASON);
			product.dropHeldQty(hold.getQty());
			notificationFunction.notify(
					hold.getUser(),
					NotificationType.HOLD_CANCELED_BY_OWNER,
					"찜이 취소됐어요",
					product.getName() + " 재고가 모자라 찜이 취소됐어요. 결제된 금액은 없어요.",
					null);
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
