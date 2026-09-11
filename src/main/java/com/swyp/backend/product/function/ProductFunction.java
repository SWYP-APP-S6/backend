package com.swyp.backend.product.function;

import com.swyp.backend.common.Distance;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.product.dto.SellableStoreGroup;
import com.swyp.backend.product.dto.StoreProductSummary;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.exception.ProductErrorCode;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductFunction {

	private final ProductRepository productRepository;
	private final Clock clock;

	public Product getByIdAndStoreId(Long productId, Long storeId) {
		return productRepository.findByIdAndStoreId(productId, storeId)
				.orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

	public Product getBrowsableById(Long productId) {
		Product product = productRepository.findWithStoreById(productId)
				.orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
		if (product.getStore().getStatus() != StoreStatus.APPROVED) {
			throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
		}
		return product;
	}

	public Product getByIdForUpdate(Long productId) {
		return productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
	}

	public Product save(Product product) {
		return productRepository.save(product);
	}

	public List<Product> findByStoreIdNewestFirst(Long storeId) {
		return productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
	}

	public List<Product> findSellableByStore(Long storeId) {
		return productRepository.findSellableByStoreId(storeId, LocalDateTime.now(clock));
	}

	public Map<Long, Long> countSellableByStore(Collection<Long> storeIds) {
		if (storeIds.isEmpty()) {
			return Map.of();
		}
		return productRepository
				.summarizeSellableByStoreIds(LocalDateTime.now(clock), storeIds).stream()
				.collect(Collectors.toMap(
						StoreProductSummary::storeId, StoreProductSummary::productCount));
	}

	public List<SellableStoreGroup> findSellableGroupedByStore(
			BigDecimal latitude, BigDecimal longitude, int radiusMeters, ProductCategory category) {
		BigDecimal latitudeDelta = Distance.latitudeDelta(radiusMeters);
		BigDecimal longitudeDelta = Distance.longitudeDelta(radiusMeters, latitude.doubleValue());

		return productRepository.findSellableWithinBounds(
						LocalDateTime.now(clock),
						category,
						latitude.subtract(latitudeDelta),
						latitude.add(latitudeDelta),
						longitude.subtract(longitudeDelta),
						longitude.add(longitudeDelta)).stream()
				.collect(Collectors.groupingBy(
						product -> product.getStore().getId(), LinkedHashMap::new, Collectors.toList()))
				.values().stream()
				.map(products -> toGroup(products, latitude, longitude))
				.filter(group -> group.distanceMeters() <= radiusMeters)
				.toList();
	}

	private static SellableStoreGroup toGroup(
			List<Product> products, BigDecimal latitude, BigDecimal longitude) {
		Store store = products.getFirst().getStore();
		int distanceMeters = (int) Math.round(Distance.metersBetween(
				latitude.doubleValue(),
				longitude.doubleValue(),
				store.getLatitude().doubleValue(),
				store.getLongitude().doubleValue()));
		return new SellableStoreGroup(store, products, distanceMeters);
	}
}
