package com.swyp.backend.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.swyp.backend.common.openapi.OpenApiErrorResponses;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String[] ADMIN_PATHS = {"/admin/**"};

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

	@Bean
	GroupedOpenApi appApi(OpenApiErrorResponses errorResponses) {
		return GroupedOpenApi.builder()
			.group("app")
			.pathsToExclude(ADMIN_PATHS)
			.addOpenApiCustomizer(errorResponses)
			.build();
	}

	@Bean
	GroupedOpenApi adminApi(OpenApiErrorResponses errorResponses) {
		return GroupedOpenApi.builder()
			.group("admin")
			.pathsToMatch(ADMIN_PATHS)
			.addOpenApiCustomizer(errorResponses)
			.build();
	}
}
