package com.swyp.backend.home.dto;

import com.swyp.backend.hold.entity.Hold;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record OwnerHomeVisit(
		Long holdId, String nickname, String summary, int totalQty, Instant expiresAt) {

	public static OwnerHomeVisit from(List<Hold> visit) {
		List<Hold> inOrderHeld = visit.stream().sorted(Comparator.comparing(Hold::getId)).toList();
		Hold first = inOrderHeld.getFirst();
		return new OwnerHomeVisit(
				first.getId(),
				first.getUser().getNickname(),
				inOrderHeld.stream()
						.map(hold -> hold.getProduct().getName())
						.collect(Collectors.joining(", ")),
				inOrderHeld.stream().mapToInt(Hold::getQty).sum(),
				inOrderHeld.stream().map(Hold::getExpiresAt).min(Comparator.naturalOrder()).orElseThrow());
	}
}
