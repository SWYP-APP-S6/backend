package com.swyp.backend.user.dto;

import com.swyp.backend.user.entity.UserLocation;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;


public record MyLocationResponse(@Nullable Region location) {

	public record Region(
			String regionName,
			BigDecimal latitude,
			BigDecimal longitude,
			Instant updatedAt) {

		static Region from(UserLocation location) {
			return new Region(
					location.getRegionName(),
					location.getLatitude(),
					location.getLongitude(),
					location.getUpdatedAt());
		}
	}

	public static MyLocationResponse of(@Nullable UserLocation location) {
		return new MyLocationResponse(location == null ? null : Region.from(location));
	}
}
