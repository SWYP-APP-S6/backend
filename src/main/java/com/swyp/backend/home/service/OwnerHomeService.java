package com.swyp.backend.home.service;

import com.swyp.backend.home.dto.OwnerHomeIssues;
import com.swyp.backend.home.dto.OwnerHomeProductCard;
import com.swyp.backend.home.dto.OwnerHomeResponse;
import com.swyp.backend.home.dto.OwnerHomeStore;
import com.swyp.backend.home.dto.OwnerHomeSummary;
import com.swyp.backend.home.dto.OwnerHomeVisit;
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
		List<OwnerHomeProductCard> productCards = products.stream()
				.map(product -> OwnerHomeProductCard.from(
						product, activeHoldQtyByProduct.getOrDefault(product.getId(), 0L)))
				.toList();

		List<OwnerHomeVisit> upcomingVisits = holdFunction.findHoldingOfStore(storeId).stream()
				.map(OwnerHomeVisit::from)
				.toList();

		OwnerHomeSummary summary = new OwnerHomeSummary(
				upcomingVisits.size(),
				holdFunction.countCompletedTodayOfStore(storeId),
				sumAvailableQty(productCards));
		OwnerHomeIssues issues =
				new OwnerHomeIssues(holdFunction.countExpiredTodayOfStore(storeId));
		long unreadNotificationCount = notificationFunction.countUnread(ownerId);
		int reconfirmPendingCount = countReconfirmPending(productCards);
		boolean hasRegisteredProduct = productFunction.hasAnyProduct(storeId);

		return new OwnerHomeResponse(
				OwnerHomeStore.from(store),
				summary,
				issues,
				unreadNotificationCount,
				reconfirmPendingCount,
				hasRegisteredProduct,
				upcomingVisits,
				productCards);
	}

	private static int sumAvailableQty(List<OwnerHomeProductCard> productCards) {
		return productCards.stream().mapToInt(OwnerHomeProductCard::availableQty).sum();
	}

	private static int countReconfirmPending(List<OwnerHomeProductCard> productCards) {
		return (int) productCards.stream().filter(OwnerHomeProductCard::reconfirmPending).count();
	}
}
