package com.swyp.backend.product.dto;

import com.swyp.backend.common.response.PageResponse;

public record NearbyProductsResponse(
		long totalProductCount, PageResponse<NearbyStoreGroupResponse> stores) {}
