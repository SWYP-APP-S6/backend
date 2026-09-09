package com.swyp.backend.product.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.dto.ActiveHoldQty;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.service.NotificationService;
import com.swyp.backend.product.dto.HoldDisposition;
import com.swyp.backend.product.dto.OwnerHomeResponse;
import com.swyp.backend.product.dto.ProductAvailableQtyUpdateRequest;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductRegisterRequest;
import com.swyp.backend.product.dto.ProductSummaryResponse;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.exception.ProductErrorCode;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.recipe.service.RecipeService;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.service.StoreService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private static final String OWNER_CANCEL_REASON = "점주가 판매를 종료해 찜이 취소됐어요.";
	private static final int MAX_PICKUP_WINDOW_HOURS = 24;

	private final ProductRepository productRepository;
	private final HoldRepository holdRepository;
	private final StoreService storeService;
	private final NotificationService notificationService;
	private final RecipeService recipeService;
	private final Clock clock;

	@Transactional
	public ProductDetailResponse registerProduct(Long ownerId, ProductRegisterRequest request) {
		Store store = storeService.validateAndGetStoreByOwnerId(ownerId);
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
		if (request.ingredientTags() != null && !recipeService.allIngredientsExist(request.ingredientTags())) {
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
		productRepository.save(product);
		return ProductDetailResponse.from(product, 0L);
	}

	public OwnerHomeResponse getHome(Long ownerId) {
		Store store = storeService.validateAndGetStoreByOwnerId(ownerId);
		List<Product> products = productRepository.findByStoreIdOrderByCreatedAtDesc(store.getId());
		Map<Long, Long> activeHoldQtyByProduct = holdRepository.findActiveHoldQtyByStoreId(store.getId()).stream()
				.collect(Collectors.toMap(ActiveHoldQty::productId, ActiveHoldQty::qty));

		List<ProductSummaryResponse> productResponses = products.stream()
				.map(product -> ProductSummaryResponse.from(product, activeHoldQtyByProduct.getOrDefault(product.getId(), 0L)))
				.toList();

		int heldQty = products.stream().mapToInt(Product::getHeldQty).sum();
		int reconfirmPendingCount = (int) products.stream()
				.filter(product -> product.getReconfirmSentAt() != null && product.getReconfirmAnsweredAt() == null)
				.count();
		long completedQty = holdRepository.sumQtyByStoreIdAndStatus(store.getId(), HoldStatus.COMPLETED);
		long activeHoldCount = holdRepository.countByStoreIdAndStatus(store.getId(), HoldStatus.HOLDING);

		return new OwnerHomeResponse(
				new OwnerHomeResponse.Summary(products.size(), heldQty, completedQty),
				reconfirmPendingCount,
				activeHoldCount,
				productResponses);
	}

	public ProductDetailResponse getMyProduct(Long ownerId, Long productId) {
		Store store = storeService.validateAndGetStoreByOwnerId(ownerId);
		Product product = validateAndGetProduct(productId, store.getId());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	@Transactional
	public ProductDetailResponse updateAvailableQty(
			Long ownerId, Long productId, ProductAvailableQtyUpdateRequest request) {
		Store store = storeService.validateAndGetStoreByOwnerId(ownerId);
		Product product = validateAndGetProduct(productId, store.getId());

		if (request.availableQty() == 0) {
			applyZeroQtyDisposition(product, request.disposition());
		}
		product.adjustAvailableQty(request.availableQty());
		return ProductDetailResponse.from(product, completedQtyOf(productId));
	}

	private void applyZeroQtyDisposition(Product product, HoldDisposition disposition) {
		List<Hold> activeHolds = holdRepository.findByProductIdAndStatus(product.getId(), HoldStatus.HOLDING);
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
				notificationService.notify(
						hold.getUser(),
						NotificationType.HOLD_CANCELED_BY_OWNER,
						"찜이 취소됐어요",
						product.getName() + " 판매가 종료되어 찜이 취소됐어요.",
						null);
			}
		}
	}

	private long completedQtyOf(Long productId) {
		return holdRepository.sumQtyByProductIdAndStatus(productId, HoldStatus.COMPLETED);
	}

	private Product validateAndGetProduct(Long productId, Long storeId) {
		return productRepository.findByIdAndStoreId(productId, storeId)
				.orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

	private static LocalDateTime defaultPickupEndAt(Store store, LocalDateTime now) {
		LocalDateTime pickupEndAt = LocalDateTime.of(now.toLocalDate(), store.getBusinessCloseTime());
		return pickupEndAt.isAfter(now) ? pickupEndAt : pickupEndAt.plusDays(1);
	}
}
