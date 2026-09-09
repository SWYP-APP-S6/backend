package com.swyp.backend.product.function;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.product.dto.StoreProductSummary;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.exception.ProductErrorCode;
import com.swyp.backend.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductFunction {

	private final ProductRepository productRepository;

	public Product getByIdAndStoreId(Long productId, Long storeId) {
		return productRepository.findByIdAndStoreId(productId, storeId)
				.orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

	public Product save(Product product) {
		return productRepository.save(product);
	}

	public List<Product> findByStoreIdNewestFirst(Long storeId) {
		return productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
	}

	public List<Product> findSellableWithinBounds(
			LocalDateTime now,
			ProductCategory category,
			BigDecimal minLatitude,
			BigDecimal maxLatitude,
			BigDecimal minLongitude,
			BigDecimal maxLongitude) {
		return productRepository.findSellableWithinBounds(
				now, category, minLatitude, maxLatitude, minLongitude, maxLongitude);
	}

	public List<Product> findSellableByStoreId(Long storeId, LocalDateTime now) {
		return productRepository.findSellableByStoreId(storeId, now);
	}

	public List<StoreProductSummary> summarizeSellableByStoreIds(
			LocalDateTime now, Collection<Long> storeIds) {
		if (storeIds.isEmpty()) {
			return List.of();
		}
		return productRepository.summarizeSellableByStoreIds(now, storeIds);
	}
}
