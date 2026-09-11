package com.swyp.backend.hold.service;

import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldExpiryService {

	private final HoldFunction holdFunction;
	private final ProductFunction productFunction;
	private final Clock clock;

	@Scheduled(
			fixedDelayString = "${hold.expiry-scan-interval}",
			initialDelayString = "${hold.expiry-scan-interval}")
	@Transactional
	public int expireOverdueHolds() {
		Map<Long, List<OverdueHold>> byProduct = holdFunction.findOverdue(Instant.now(clock)).stream()
				.collect(Collectors.groupingBy(
						OverdueHold::productId, LinkedHashMap::new, Collectors.toList()));

		int expired = 0;
		for (Map.Entry<Long, List<OverdueHold>> entry : byProduct.entrySet()) {
			Product product = productFunction.getByIdForUpdate(entry.getKey());
			for (OverdueHold overdue : entry.getValue()) {
				expired += expire(product, overdue.holdId()) ? 1 : 0;
			}
		}
		if (expired > 0) {
			log.info("Expired {} overdue holds", expired);
		}
		return expired;
	}

	private boolean expire(Product product, Long holdId) {
		Hold hold = holdFunction.getByIdForUpdate(holdId);
		if (hold.getStatus() != HoldStatus.HOLDING) {
			return false;
		}
		hold.expire();
		product.releaseHold(hold.getQty());
		return true;
	}
}
