package com.swyp.backend.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata + a global {@code bearerAuth} scheme so the Swagger UI "Authorize" button accepts
 * a JWT access token and sends it on protected endpoints.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI openAPI() {
		SecurityScheme bearer = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT");
		return new OpenAPI()
			.info(new Info().title("SWYP Backend API").version("v1"))
			.components(new Components().addSecuritySchemes("bearerAuth", bearer))
			.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
	}
}
