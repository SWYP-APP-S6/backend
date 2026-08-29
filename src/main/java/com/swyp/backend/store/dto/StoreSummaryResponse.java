package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.Store;
import java.time.Instant;
import java.time.LocalTime;

public record StoreSummaryResponse(
		Long id,
		String name,
		String status,
		String address,
		String addressDetail,
		String phone,
		LocalTime businessOpenTime,
		LocalTime businessCloseTime,
		Owner owner,
		Instant createdAt) {

	public record Owner(Long id, String nickname, String phone) {
	}

	public static StoreSummaryResponse from(Store store) {
		return new StoreSummaryResponse(
				store.getId(),
				store.getName(),
				store.getStatus().name(),
				store.getAddress(),
				store.getAddressDetail(),
				store.getPhone(),
				store.getBusinessOpenTime(),
				store.getBusinessCloseTime(),
				new Owner(
						store.getOwner().getId(),
						store.getOwner().getNickname(),
						store.getOwner().getPhone()),
				store.getCreatedAt());
	}
}
