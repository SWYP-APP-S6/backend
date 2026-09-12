package com.swyp.backend.hold.service;

import com.swyp.backend.hold.dto.OverdueHold;
import com.swyp.backend.hold.function.HoldFunction;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldExpiryService {

	private final HoldFunction holdFunction;
	private final HoldExpirer holdExpirer;
	private final Clock clock;

	@Scheduled(
			fixedDelayString = "${hold.expiry-scan-interval}",
			initialDelayString = "${hold.expiry-scan-interval}")
	public int expireOverdueHolds() {
		Map<Long, List<Long>> productIdsByHold = holdFunction.findOverdue(Instant.now(clock)).stream()
				.collect(Collectors.groupingBy(
						OverdueHold::holdId,
						LinkedHashMap::new,
						Collectors.mapping(OverdueHold::productId, Collectors.toList())));

		int expired = 0;
		for (Map.Entry<Long, List<Long>> entry : productIdsByHold.entrySet()) {
			expired += holdExpirer.expire(entry.getKey(), entry.getValue()) ? 1 : 0;
		}
		if (expired > 0) {
			log.info("Expired {} overdue holds", expired);
		}
		return expired;
	}
}
