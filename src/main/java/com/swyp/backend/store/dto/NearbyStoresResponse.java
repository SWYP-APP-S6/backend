package com.swyp.backend.store.dto;

import java.util.List;

public record NearbyStoresResponse(
		int totalCount, boolean truncated, List<NearbyStoreMarkerResponse> stores) {}
