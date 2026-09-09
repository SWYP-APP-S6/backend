package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

// 신청서 본문은 길 수 있어 목록 응답에 싣지 않는다 — 관리자가 실제로 열어볼 때만 가져온다.
public record StoreDetailResponse(
		Long id,
		String name,
		String status,
		List<StoreCategory> categories,
		String postalCode,
		String address,
		String addressDetail,
		String phone,
		LocalTime businessOpenTime,
		LocalTime businessCloseTime,
		List<DayOfWeek> businessDays,
		String businessRegistrationNumber,
		String applicationNote,
		StoreSummaryResponse.Owner owner,
		Instant createdAt) {

	public static StoreDetailResponse from(Store store) {
		return new StoreDetailResponse(
				store.getId(),
				store.getName(),
				store.getStatus().name(),
				store.getCategories().stream().sorted().toList(),
				store.getPostalCode(),
				store.getAddress(),
				store.getAddressDetail(),
				store.getPhone(),
				store.getBusinessOpenTime(),
				store.getBusinessCloseTime(),
				store.getBusinessDays().stream().sorted().toList(),
				store.getBusinessRegistrationNumber(),
				store.getApplicationNote(),
				new StoreSummaryResponse.Owner(
						store.getOwner().getId(),
						store.getOwner().getNickname(),
						store.getOwner().getPhone()),
				store.getCreatedAt());
	}
}
