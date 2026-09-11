package com.swyp.backend.home.dto;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import java.time.Instant;
import java.util.List;

public record OwnerHomeResponse(
		StoreSummary store,
		Summary summary,
		Issues issues,
		long unreadNotificationCount,
		int reconfirmPendingCount,
		boolean hasRegisteredProduct,
		List<UpcomingVisit> upcomingVisits,
		List<ProductCard> products) {

	public record StoreSummary(Long id, String name, String status, List<StoreCategory> categories) {

		public static StoreSummary from(Store store) {
			return new StoreSummary(
					store.getId(),
					store.getName(),
					store.getStatus().name(),
					store.getCategories().stream().sorted().toList());
		}
	}

	public record Summary(int upcomingVisitCount, long completedTodayCount, int onSaleQty) {
	}

	public record Issues(long oversoldQty, long expiredTodayCount) {
	}

	public record UpcomingVisit(
			Long holdId, String nickname, String productName, int qty, Instant expiresAt) {

		public static UpcomingVisit from(Hold hold) {
			return new UpcomingVisit(
					hold.getId(),
					hold.getUser().getNickname(),
					hold.getProduct().getName(),
					hold.getQty(),
					hold.getExpiresAt());
		}
	}

	public record ProductCard(
			Long id,
			String name,
			String category,
			String photoUrl,
			int salePrice,
			int availableQty,
			long activeHoldQty,
			long oversoldQty,
			String status,
			boolean reconfirmPending) {

		public static ProductCard from(Product product, long activeHoldQty) {
			return new ProductCard(
					product.getId(),
					product.getName(),
					product.getCategory().name(),
					product.getPhotoUrl(),
					product.getSalePrice(),
					product.getAvailableQty(),
					activeHoldQty,
					Math.max(0L, activeHoldQty - product.getAvailableQty()),
					product.getStatus().name(),
					product.getReconfirmSentAt() != null && product.getReconfirmAnsweredAt() == null);
		}
	}
}
