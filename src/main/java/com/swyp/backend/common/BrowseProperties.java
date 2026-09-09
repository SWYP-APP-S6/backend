package com.swyp.backend.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "browse")
public record BrowseProperties(
		int nearbyRadiusMeters, int mapMarkerLimit, int maxViewportSpanMeters) {

	public BrowseProperties {
		if (nearbyRadiusMeters <= 0 || mapMarkerLimit <= 0 || maxViewportSpanMeters <= 0) {
			throw new IllegalArgumentException(
					"browse.{nearby-radius-meters,map-marker-limit,max-viewport-span-meters}"
							+ " must be positive");
		}
	}
}
