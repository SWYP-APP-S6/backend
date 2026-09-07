package com.swyp.backend.store.service;

import java.math.BigDecimal;

public interface GeocodingClient {

	Coordinates geocode(String address);

	record Coordinates(BigDecimal latitude, BigDecimal longitude) {
	}
}
