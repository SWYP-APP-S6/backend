package com.swyp.backend.common.openapi;

import com.swyp.backend.common.response.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

@Component
public class OpenApiErrorResponses implements OpenApiCustomizer {

	private static final String SCHEMA_NAME = "ErrorResponse";
	private static final String REF = "#/components/schemas/" + SCHEMA_NAME;

	private static final List<String> ALWAYS_PRESENT = List.of("status", "code", "message");

	private static final Map<String, String> ALWAYS = new LinkedHashMap<>(Map.of(
			"400", "요청이 올바르지 않습니다. code=VALIDATION_FAILED 이면 fieldErrors 에 필드별 사유가 담깁니다.",
			"401", "인증이 필요하거나 토큰이 유효하지 않습니다.",
			"403", "권한이 없습니다. guest 토큰으로 회원 전용 기능을 호출하면 code=LOGIN_REQUIRED 입니다.",
			"429", "요청이 너무 많습니다. Retry-After 헤더의 초만큼 기다린 뒤 재시도합니다.",
			"500", "서버 내부 오류입니다."));

	@Override
	public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
		registerSchema(openApi);
		openApi.getPaths().values().stream()
				.flatMap(pathItem -> pathItem.readOperations().stream())
				.forEach(OpenApiErrorResponses::addErrorResponses);
	}

	private static void registerSchema(io.swagger.v3.oas.models.OpenAPI openApi) {
		if (openApi.getComponents().getSchemas() != null
				&& openApi.getComponents().getSchemas().containsKey(SCHEMA_NAME)) {
			return;
		}
		ResolvedSchema resolved = ModelConverters.getInstance()
				.resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class));
		resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);

		Schema<?> errorSchema = openApi.getComponents().getSchemas().get(SCHEMA_NAME);
		if (errorSchema != null && errorSchema.getRequired() == null) {
			ALWAYS_PRESENT.forEach(errorSchema::addRequiredItem);
		}
	}

	private static void addErrorResponses(Operation operation) {
		ApiResponses responses = operation.getResponses();
		ALWAYS.forEach((status, description) -> {
			if (responses.containsKey(status)) {
				return;
			}
			responses.addApiResponse(status, new ApiResponse()
					.description(description)
					.content(new Content().addMediaType("application/json",
							new MediaType().schema(new Schema<>().$ref(REF)))));
		});
	}
}
