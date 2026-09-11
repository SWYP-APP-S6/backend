package com.swyp.backend.home.dto;

import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import java.util.List;

public record OwnerHomeStore(Long id, String name, String status, List<StoreCategory> categories) {

	public static OwnerHomeStore from(Store store) {
		return new OwnerHomeStore(
				store.getId(),
				store.getName(),
				store.getStatus().name(),
				store.getCategories().stream().sorted().toList());
	}
}
