package com.swyp.backend.product.service;

import com.swyp.backend.common.BrowseProperties;
import com.swyp.backend.common.Distance;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.product.dto.NearbyProductSort;
import com.swyp.backend.product.dto.NearbyProductsRequest;
import com.swyp.backend.product.dto.NearbyProductsResponse;
import com.swyp.backend.product.dto.NearbyStoreGroupResponse;
import com.swyp.backend.product.dto.SellableProductResponse;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductBrowseService {

	private static final int MAX_PRODUCTS_PER_STORE = 10;

	private final ProductFunction productFunction;
	private final Clock clock;
	private final BrowseProperties browseProperties;

	public NearbyProductsResponse findNearby(NearbyProductsRequest request) {
		int radiusMeters = browseProperties.nearbyRadiusMeters();
		double originLatitude = request.lat().doubleValue();
		BigDecimal latitudeDelta = Distance.latitudeDelta(radiusMeters);
		BigDecimal longitudeDelta = Distance.longitudeDelta(radiusMeters, originLatitude);

		List<Product> sellable = productFunction.findSellableWithinBounds(
				LocalDateTime.now(clock),
				request.category(),
				request.lat().subtract(latitudeDelta),
				request.lat().add(latitudeDelta),
				request.lng().subtract(longitudeDelta),
				request.lng().add(longitudeDelta));

		List<NearbyStoreGroupResponse> groups = sellable.stream()
				.collect(Collectors.groupingBy(
						product -> product.getStore().getId(), LinkedHashMap::new, Collectors.toList()))
				.values().stream()
				.map(products -> toStoreGroup(products, request.lat(), request.lng()))
				.filter(group -> group.distanceMeters() <= radiusMeters)
				.sorted(comparatorFor(request.sort()))
				.toList();

		long totalProductCount = groups.stream()
				.mapToLong(NearbyStoreGroupResponse::productCount)
				.sum();

		PageRequest pageRequest = PageRequest.of(request.page(), request.size());
		int fromIndex = Math.min((int) pageRequest.getOffset(), groups.size());
		int toIndex = Math.min(fromIndex + request.size(), groups.size());
		Page<NearbyStoreGroupResponse> page =
				new PageImpl<>(groups.subList(fromIndex, toIndex), pageRequest, groups.size());
		return new NearbyProductsResponse(
				totalProductCount, PageResponse.of(page.getContent(), page));
	}

	private NearbyStoreGroupResponse toStoreGroup(
			List<Product> products, BigDecimal originLatitude, BigDecimal originLongitude) {
		Store store = products.getFirst().getStore();
		int distanceMeters = (int) Math.round(Distance.metersBetween(
				originLatitude.doubleValue(),
				originLongitude.doubleValue(),
				store.getLatitude().doubleValue(),
				store.getLongitude().doubleValue()));
		List<SellableProductResponse> visible = products.stream()
				.limit(MAX_PRODUCTS_PER_STORE)
				.map(SellableProductResponse::from)
				.toList();
		return new NearbyStoreGroupResponse(
				store.getId(),
				store.getName(),
				distanceMeters,
				Distance.straightLineWalkingMinutes(distanceMeters),
				products.size(),
				products.size() > visible.size(),
				products.stream()
						.map(Product::getPickupEndAt)
						.min(Comparator.naturalOrder())
						.orElseThrow(),
				visible);
	}

	private static Comparator<NearbyStoreGroupResponse> comparatorFor(NearbyProductSort sort) {
		return switch (sort) {
			case DISTANCE -> Comparator.comparingInt(NearbyStoreGroupResponse::distanceMeters)
					.thenComparing(NearbyStoreGroupResponse::storeId);
			case PICKUP_DEADLINE -> Comparator
					.comparing(NearbyStoreGroupResponse::earliestPickupEndAt)
					.thenComparing(NearbyStoreGroupResponse::storeId);
		};
	}
}
