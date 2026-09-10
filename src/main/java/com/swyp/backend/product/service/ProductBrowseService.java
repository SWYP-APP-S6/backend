package com.swyp.backend.product.service;

import com.swyp.backend.common.BrowseProperties;
import com.swyp.backend.product.dto.NearbyProductSort;
import com.swyp.backend.product.dto.NearbyProductsRequest;
import com.swyp.backend.product.dto.NearbyProductsResponse;
import com.swyp.backend.product.dto.NearbyStoreGroupResponse;
import com.swyp.backend.product.dto.SellableStoreGroup;
import com.swyp.backend.product.function.ProductFunction;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductBrowseService {

	private final ProductFunction productFunction;
	private final BrowseProperties browseProperties;

	public NearbyProductsResponse findNearby(NearbyProductsRequest request) {
		List<SellableStoreGroup> nearby = productFunction.findSellableGroupedByStore(
				request.lat(),
				request.lng(),
				browseProperties.nearbyRadiusMeters(),
				request.category());

		List<NearbyStoreGroupResponse> groups = nearby.stream()
				.sorted(comparatorFor(request.sort()))
				.map(NearbyStoreGroupResponse::from)
				.toList();

		return NearbyProductsResponse.of(groups, request.page(), request.size());
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
