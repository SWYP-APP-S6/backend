package com.swyp.backend.product.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.storage.ImageStorage;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.product.dto.OwnerProductFilter;
import com.swyp.backend.product.dto.OwnerProductListResponse;
import com.swyp.backend.product.dto.OwnerProductSummaryResponse;
import com.swyp.backend.product.dto.ProductDetailResponse;
import com.swyp.backend.product.dto.ProductPhotoResponse;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private static final int MAX_PICKUP_WINDOW_HOURS = 24;
	private static final String PHOTO_CATEGORY = "products";

	private final ProductFunction productFunction;
	private final HoldFunction holdFunction;
	private final StoreFunction storeFunction;
	private final RecipeFunction recipeFunction;
	private final ImageStorage imageStorage;
	private final Clock clock;

	@Transactional
	public ProductDetailResponse registerProduct(Long ownerId, ProductRegisterRequest request) {
		Product product = buildProduct(ownerId, request);
		productFunction.save(product);
		return ProductDetailResponse.from(product, 0L, LocalDateTime.now(clock));
	}

	public ProductPreviewResponse previewProduct(Long ownerId, ProductRegisterRequest request) {
		return ProductPreviewResponse.from(buildProduct(ownerId, request));
	}

	public ProductPhotoResponse uploadPhoto(Long ownerId, MultipartFile file) {
		storeFunction.getByOwnerId(ownerId);
		return new ProductPhotoResponse(imageStorage.store(file, PHOTO_CATEGORY));
	}

	private Product buildProduct(Long ownerId, ProductRegisterRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		if (request.salePrice() >= request.originalPrice()) {
			throw new BusinessException(ProductErrorCode.INVALID_PRICE);
		}
		if (!imageStorage.holds(request.photoUrl())) {
			throw new BusinessException(ProductErrorCode.INVALID_PHOTO_URL);
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

	public OwnerProductListResponse getMyProducts(
			Long ownerId, OwnerProductFilter filter, Pageable pageable) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Page<Product> products = productFunction.findStoreProducts(store.getId(), filter, pageable);
		Map<Long, Long> activeHoldQtyByProduct =
				holdFunction.activeQtyByProductOfStore(store.getId());
		Map<Long, Long> shortfallCustomersByProduct = holdFunction.customersNotServedByProduct(
				products.getContent().stream()
						.filter(product -> product.shortfallQty() > 0)
						.collect(Collectors.toMap(Product::getId, Product::getStockQty)));
		List<OwnerProductSummaryResponse> content = products.getContent().stream()
				.map(product -> OwnerProductSummaryResponse.from(
						product,
						activeHoldQtyByProduct.getOrDefault(product.getId(), 0L),
						shortfallCustomersByProduct.getOrDefault(product.getId(), 0L)))
				.toList();
		return new OwnerProductListResponse(Instant.now(clock), PageResponse.of(content, products));
	}

	public ProductDetailResponse getMyProduct(Long ownerId, Long productId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdAndStoreId(productId, store.getId());
		return ProductDetailResponse.from(
				product, completedQtyOf(productId), LocalDateTime.now(clock));
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
		LocalDateTime now = LocalDateTime.now(clock);
		if (product.isStockLocked(now)) {
			throw new BusinessException(ProductErrorCode.STOCK_LOCKED);
		}
		if (request.stockQty() < product.minAdjustableQty()) {
			throw new BusinessException(ProductErrorCode.QTY_BELOW_MINIMUM);
		}

		product.restock(request.stockQty());
		return ProductDetailResponse.from(product, completedQtyOf(productId), now);
	}

	@Transactional
	public ProductDetailResponse answerStockReconfirm(
			Long ownerId, Long productId, StockReconfirmRequest request) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Product product = productFunction.getByIdForUpdate(productId);
		if (!product.getStore().getId().equals(store.getId())) {
			throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
		}
		if (product.getStatus() == ProductStatus.CLOSED) {
			throw new BusinessException(ProductErrorCode.PRODUCT_CLOSED);
		}
		if (!product.isStockReconfirmPending()) {
			throw new BusinessException(ProductErrorCode.RECONFIRM_NOT_REQUESTED);
		}

		Instant now = Instant.now(clock);
		if (request.confirmed()) {
			if (product.shortfallQty() > 0) {
				throw new BusinessException(ProductErrorCode.STOCK_SHORT_OF_HOLDS);
			}
			product.confirmStock(now);
		} else {
			product.denyStockConfirmation(now);
		}
		return ProductDetailResponse.from(
				product, completedQtyOf(productId), LocalDateTime.now(clock));
	}

	private long completedQtyOf(Long productId) {
		return holdFunction.completedQtyOfProduct(productId);
	}


	private static LocalDateTime defaultPickupEndAt(Store store, LocalDateTime now) {
		LocalDateTime pickupEndAt = LocalDateTime.of(now.toLocalDate(), store.getBusinessCloseTime());
		return pickupEndAt.isAfter(now) ? pickupEndAt : pickupEndAt.plusDays(1);
	}
}
