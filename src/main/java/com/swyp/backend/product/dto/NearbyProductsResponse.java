package com.swyp.backend.product.dto;

import com.swyp.backend.common.response.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

public record NearbyProductsResponse(
		long totalProductCount, PageResponse<NearbyStoreGroupResponse> stores) {

	public static NearbyProductsResponse of(
			List<NearbyStoreGroupResponse> groups, int page, int size) {
		long totalProductCount = groups.stream()
				.mapToLong(NearbyStoreGroupResponse::productCount)
				.sum();

		PageRequest pageRequest = PageRequest.of(page, size);
		int fromIndex = Math.min((int) pageRequest.getOffset(), groups.size());
		int toIndex = Math.min(fromIndex + size, groups.size());
		PageImpl<NearbyStoreGroupResponse> pageOfGroups =
				new PageImpl<>(groups.subList(fromIndex, toIndex), pageRequest, groups.size());
		return new NearbyProductsResponse(
				totalProductCount, PageResponse.of(pageOfGroups.getContent(), pageOfGroups));
	}
}
