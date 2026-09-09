package com.swyp.backend.common;

import java.math.BigDecimal;

public final class Distance {

	private static final double EARTH_RADIUS_METERS = 6_371_000d;
	private static final double METERS_PER_LATITUDE_DEGREE = 111_320d;
	private static final double MIN_LONGITUDE_SCALE = 0.01d;
	private static final double WALKING_METERS_PER_MINUTE = 67d;

	private Distance() {
	}

	public static double metersBetween(
			double originLatitude, double originLongitude, double latitude, double longitude) {
		double latitudeDelta = Math.toRadians(latitude - originLatitude);
		double longitudeDelta = Math.toRadians(longitude - originLongitude);
		double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
				+ Math.cos(Math.toRadians(originLatitude))
						* Math.cos(Math.toRadians(latitude))
						* Math.pow(Math.sin(longitudeDelta / 2), 2);
		return EARTH_RADIUS_METERS * 2 * Math.asin(Math.min(1d, Math.sqrt(a)));
	}

	public static int straightLineWalkingMinutes(int meters) {
		return Math.max(1, (int) Math.ceil(meters / WALKING_METERS_PER_MINUTE));
	}

	public static BigDecimal latitudeDelta(int meters) {
		return BigDecimal.valueOf(meters / METERS_PER_LATITUDE_DEGREE);
	}

	public static BigDecimal longitudeDelta(int meters, double atLatitude) {
		return BigDecimal.valueOf(meters
				/ (METERS_PER_LATITUDE_DEGREE
						* Math.max(Math.cos(Math.toRadians(atLatitude)), MIN_LONGITUDE_SCALE)));
	}
}
