package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.Store;
import java.time.Instant;
import java.time.LocalTime;

// 신청서 본문은 길 수 있어 목록 응답에 싣지 않는다 — 관리자가 실제로 열어볼 때만 가져온다.
public record StoreDetailResponse(
		Long id,
		String name,
		String status,
		String address,
		String addressDetail,
		String phone,
		LocalTime businessOpenTime,
		LocalTime businessCloseTime,
		String businessRegistrationNumber,
		String applicationNote,
		StoreSummaryResponse.Owner owner,
		Instant createdAt) {

	public static StoreDetailResponse from(Store store) {
		return new StoreDetailResponse(
				store.getId(),
				store.getName(),
				store.getStatus().name(),
				store.getAddress(),
				store.getAddressDetail(),
				store.getPhone(),
				store.getBusinessOpenTime(),
				store.getBusinessCloseTime(),
				store.getBusinessRegistrationNumber(),
				store.getApplicationNote(),
				new StoreSummaryResponse.Owner(
						store.getOwner().getId(),
						store.getOwner().getNickname(),
						store.getOwner().getPhone()),
				store.getCreatedAt());
	}
}
