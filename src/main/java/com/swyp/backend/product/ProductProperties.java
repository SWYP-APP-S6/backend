package com.swyp.backend.product;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product")
public record ProductProperties(int runningLowQty) {

	public ProductProperties {
		if (runningLowQty <= 0) {
			throw new IllegalArgumentException("product.running-low-qty must be positive");
		}
	}
}
