package com.swyp.backend.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.image")
public record ImageStorageProperties(String rootDir, String publicBaseUrl) {

	public ImageStorageProperties {
		publicBaseUrl = publicBaseUrl.endsWith("/")
				? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
				: publicBaseUrl;
	}
}
