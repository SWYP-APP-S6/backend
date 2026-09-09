package com.swyp.backend.product.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.product.dto.NearbyProductResponse;
import com.swyp.backend.product.dto.NearbyProductSort;
import com.swyp.backend.product.dto.NearbyProductsRequest;
import com.swyp.backend.product.dto.NearbyProductsResponse;
import com.swyp.backend.product.dto.NearbyStoreGroupResponse;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductBrowseService {

	private static final double EARTH_RADIUS_METERS = 6_371_000d;
	private static final double METERS_PER_LATITUDE_DEGREE = 111_320d;
	private static final double MIN_LONGITUDE_SCALE = 0.01d;
	private static final double WALKING_METERS_PER_MINUTE = 67d;
	private static final int MAX_PRODUCTS_PER_STORE = 10;

	private final ProductRepository productRepository;
	private final int radiusMeters;

	public ProductBrowseService(
			ProductRepository productRepository,
			@Value("${browse.nearby-radius-meters}") int radiusMeters) {
		this.productRepository = productRepository;
		this.radiusMeters = radiusMeters;
	}

	public NearbyProductsResponse findNearby(NearbyProductsRequest request) {
		double originLatitude = request.lat().doubleValue();
		BigDecimal latitudeDelta = BigDecimal.valueOf(radiusMeters / METERS_PER_LATITUDE_DEGREE);
		BigDecimal longitudeDelta = BigDecimal.valueOf(radiusMeters
				/ (METERS_PER_LATITUDE_DEGREE
						* Math.max(Math.cos(Math.toRadians(originLatitude)), MIN_LONGITUDE_SCALE)));

		List<Product> sellable = productRepository.findSellableWithinBounds(
				LocalDateTime.now(),
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
		int distanceMeters = (int) Math.round(distanceMeters(
				originLatitude.doubleValue(),
				originLongitude.doubleValue(),
				store.getLatitude().doubleValue(),
				store.getLongitude().doubleValue()));
		List<NearbyProductResponse> visible = products.stream()
				.limit(MAX_PRODUCTS_PER_STORE)
				.map(NearbyProductResponse::from)
				.toList();
		return new NearbyStoreGroupResponse(
				store.getId(),
				store.getName(),
				distanceMeters,
				walkingMinutes(distanceMeters),
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

	private static int walkingMinutes(int distanceMeters) {
		return Math.max(1, (int) Math.ceil(distanceMeters / WALKING_METERS_PER_MINUTE));
	}

	private static double distanceMeters(
			double originLatitude, double originLongitude, double latitude, double longitude) {
		double latitudeDelta = Math.toRadians(latitude - originLatitude);
		double longitudeDelta = Math.toRadians(longitude - originLongitude);
		double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
				+ Math.cos(Math.toRadians(originLatitude))
						* Math.cos(Math.toRadians(latitude))
						* Math.pow(Math.sin(longitudeDelta / 2), 2);
		return EARTH_RADIUS_METERS * 2 * Math.asin(Math.min(1d, Math.sqrt(a)));
	}
}
