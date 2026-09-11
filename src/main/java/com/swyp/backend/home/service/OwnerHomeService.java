package com.swyp.backend.home.service;

import com.swyp.backend.home.dto.OwnerHomeResponse;
import com.swyp.backend.home.dto.OwnerHomeResponse.Issues;
import com.swyp.backend.home.dto.OwnerHomeResponse.ProductCard;
import com.swyp.backend.home.dto.OwnerHomeResponse.StoreSummary;
import com.swyp.backend.home.dto.OwnerHomeResponse.Summary;
import com.swyp.backend.home.dto.OwnerHomeResponse.UpcomingVisit;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.function.StoreFunction;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OwnerHomeService {

	private final StoreFunction storeFunction;
	private final ProductFunction productFunction;
	private final HoldFunction holdFunction;
	private final NotificationFunction notificationFunction;

	public OwnerHomeResponse getOwnerHome(Long ownerId) {
		Store store = storeFunction.getByOwnerId(ownerId);
		Long storeId = store.getId();

		List<Product> products = productFunction.findSellingNowOfStore(storeId);
		Map<Long, Long> activeHoldQtyByProduct = holdFunction.activeQtyByProductOfStore(storeId);
		List<ProductCard> productCards = products.stream()
				.map(product -> ProductCard.from(
						product, activeHoldQtyByProduct.getOrDefault(product.getId(), 0L)))
				.toList();

		List<UpcomingVisit> upcomingVisits = holdFunction.findHoldingOfStore(storeId).stream()
				.map(UpcomingVisit::from)
				.toList();

		return new OwnerHomeResponse(
				StoreSummary.from(store),
				new Summary(
						upcomingVisits.size(),
						holdFunction.countCompletedTodayOfStore(storeId),
						sumAvailableQty(productCards)),
				new Issues(
						sumOversoldQty(productCards),
						holdFunction.countExpiredTodayOfStore(storeId)),
				notificationFunction.countUnread(ownerId),
				countReconfirmPending(productCards),
				productFunction.hasAnyProduct(storeId),
				upcomingVisits,
				productCards);
	}

	private static int sumAvailableQty(List<ProductCard> productCards) {
		return productCards.stream().mapToInt(ProductCard::availableQty).sum();
	}

	private static long sumOversoldQty(List<ProductCard> productCards) {
		return productCards.stream().mapToLong(ProductCard::oversoldQty).sum();
	}

	private static int countReconfirmPending(List<ProductCard> productCards) {
		return (int) productCards.stream().filter(ProductCard::reconfirmPending).count();
	}
}
