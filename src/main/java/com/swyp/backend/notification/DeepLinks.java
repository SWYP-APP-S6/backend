package com.swyp.backend.notification;

public final class DeepLinks {

	private static final String SCHEME = "mangro://";

	private DeepLinks() {
	}

	public static String consumerHold(Long holdId) {
		return SCHEME + "holds/" + holdId;
	}

	public static String ownerHold(Long holdId) {
		return SCHEME + "owner/holds/" + holdId;
	}

	public static String ownerProduct(Long productId) {
		return SCHEME + "owner/products/" + productId;
	}
}
