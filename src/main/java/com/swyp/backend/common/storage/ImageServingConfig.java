package com.swyp.backend.common.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ImageServingConfig implements WebMvcConfigurer {

	private final Path root;

	public ImageServingConfig(@Value("${storage.image.root-dir}") String rootDir) {
		this.root = Path.of(rootDir).toAbsolutePath().normalize();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/**").addResourceLocations(root.toUri().toString());
	}
}
