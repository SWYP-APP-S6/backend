package com.swyp.backend.home.dto;

import java.time.Instant;
import java.util.List;

public record OwnerHomeResponse(
		Instant serverTime,
		OwnerHomeStore store,
		OwnerHomeSummary summary,
		OwnerHomeIssues issues,
		long unreadNotificationCount,
		int reconfirmPendingCount,
		boolean hasRegisteredProduct,
		List<OwnerHomeVisit> upcomingVisits,
		List<OwnerHomeProductCard> products) {
}
