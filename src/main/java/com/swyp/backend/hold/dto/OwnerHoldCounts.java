package com.swyp.backend.hold.dto;

import java.util.Map;

public record OwnerHoldCounts(
		long all,
		long holding,
		long completed,
		long expired,
		long canceledByOwner,
		long canceledByUser) {

	public static OwnerHoldCounts from(Map<OwnerHoldStatus, Long> countByStatus) {
		return new OwnerHoldCounts(
				countByStatus.values().stream().mapToLong(Long::longValue).sum(),
				countOf(countByStatus, OwnerHoldStatus.HOLDING),
				countOf(countByStatus, OwnerHoldStatus.COMPLETED),
				countOf(countByStatus, OwnerHoldStatus.EXPIRED),
				countOf(countByStatus, OwnerHoldStatus.CANCELED_BY_OWNER),
				countOf(countByStatus, OwnerHoldStatus.CANCELED_BY_USER));
	}

	private static long countOf(Map<OwnerHoldStatus, Long> countByStatus, OwnerHoldStatus status) {
		return countByStatus.getOrDefault(status, 0L);
	}
}
