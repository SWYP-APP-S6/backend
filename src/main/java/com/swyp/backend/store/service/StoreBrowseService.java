package com.swyp.backend.store.service;

import com.swyp.backend.common.BrowseProperties;
import com.swyp.backend.common.Distance;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.product.dto.SellableProductResponse;
import com.swyp.backend.product.dto.StoreProductSummary;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.dto.NearbyStoreMarkerResponse;
import com.swyp.backend.store.dto.NearbyStoresRequest;
import com.swyp.backend.store.dto.NearbyStoresResponse;
import com.swyp.backend.store.dto.StoreProductsRequest;
import com.swyp.backend.store.dto.StoreProductsResponse;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.exception.StoreErrorCode;
import com.swyp.backend.store.function.StoreFunction;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreBrowseService {

	private final StoreFunction storeFunction;
	private final ProductFunction productFunction;
	private final BrowseProperties browseProperties;
	private final Clock clock;

	public NearbyStoresResponse findNearbyStores(NearbyStoresRequest request) {
		double centerLatitude = request.centerLat().doubleValue();
		double centerLongitude = request.centerLng().doubleValue();
		requireViewportWithinSpan(request, centerLatitude);

		List<Store> stores = storeFunction.findApprovedWithinBounds(
				request.minLat(), request.maxLat(), request.minLng(), request.maxLng());
		Map<Long, StoreProductSummary> summaries = productFunction
				.summarizeSellableByStoreIds(
					LocalDateTime.now(clock), stores.stream().map(Store::getId).toList())
				.stream()
				.collect(Collectors.toMap(StoreProductSummary::storeId, summary -> summary));

		Map<Long, Double> distances = new HashMap<>();
		for (Store store : stores) {
			distances.put(store.getId(), Distance.metersBetween(
					centerLatitude, centerLongitude,
					store.getLatitude().doubleValue(), store.getLongitude().doubleValue()));
		}

		List<NearbyStoreMarkerResponse> markers = stores.stream()
				.filter(store -> summaries.containsKey(store.getId()))
				.sorted(Comparator.comparingDouble((Store store) -> distances.get(store.getId()))
					.thenComparing(Store::getId))
				.map(store -> NearbyStoreMarkerResponse.from(
						store, summaries.get(store.getId()).productCount().intValue()))
				.toList();

		int limit = browseProperties.mapMarkerLimit();
		return new NearbyStoresResponse(
				markers.size(), markers.size() > limit, markers.stream().limit(limit).toList());
	}

	public StoreProductsResponse getStoreProducts(Long storeId, StoreProductsRequest request) {
		Store store = validateAndGetApprovedStore(storeId);
		List<SellableProductResponse> products =
				productFunction.findSellableByStoreId(storeId, LocalDateTime.now(clock)).stream()
					.map(SellableProductResponse::from)
					.toList();

		Integer distanceMeters = null;
		if (request.hasPosition()) {
			distanceMeters = (int) Math.round(Distance.metersBetween(
					request.lat().doubleValue(), request.lng().doubleValue(),
					store.getLatitude().doubleValue(), store.getLongitude().doubleValue()));
		}
		return StoreProductsResponse.from(store, products, distanceMeters);
	}

	private void requireViewportWithinSpan(NearbyStoresRequest request, double centerLatitude) {
		int maxSpan = browseProperties.maxViewportSpanMeters();
		double latitudeSpan = Distance.metersBetween(
				request.minLat().doubleValue(), 0, request.maxLat().doubleValue(), 0);
		double longitudeSpan = Distance.metersBetween(
				centerLatitude, request.minLng().doubleValue(),
				centerLatitude, request.maxLng().doubleValue());
		if (latitudeSpan > maxSpan || longitudeSpan > maxSpan) {
			throw new BusinessException(StoreErrorCode.VIEWPORT_TOO_LARGE);
		}
	}

	private Store validateAndGetApprovedStore(Long storeId) {
		Store store = storeFunction.getById(storeId);
		if (store.getStatus() != StoreStatus.APPROVED) {
			throw new BusinessException(StoreErrorCode.STORE_NOT_FOUND);
		}
		return store;
	}
}
