package com.swyp.backend.common.openapi;

import com.swyp.backend.common.response.ApiCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class OpenApiBusinessErrors implements OperationCustomizer {

	private static final String REF = "#/components/schemas/ErrorResponse";

	private static final String MEDIA_TYPE = "application/json";

	@Override
	public Operation customize(Operation operation, HandlerMethod handlerMethod) {
		Set<ApiErrorCodes> declared = AnnotatedElementUtils.findMergedRepeatableAnnotations(
				handlerMethod.getMethod(), ApiErrorCodes.class);
		Map<String, List<ApiCode>> byStatus = new LinkedHashMap<>();
		for (ApiErrorCodes group : declared) {
			for (String name : group.codes()) {
				ApiCode code = resolve(group.in(), name, handlerMethod);
				byStatus.computeIfAbsent(
						String.valueOf(code.getStatus().value()), status -> new ArrayList<>()).add(code);
			}
		}
		byStatus.forEach((status, codes) -> describe(operation, status, codes));
		return operation;
	}

	private static ApiCode resolve(
			Class<? extends ApiCode> owner, String name, HandlerMethod handlerMethod) {
		ApiCode[] constants = owner.getEnumConstants();
		if (constants == null) {
			throw new IllegalStateException(owner.getName() + " is not an enum of error codes");
		}
		return Arrays.stream(constants)
				.filter(code -> code.name().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(handlerMethod.getMethod().getName()
						+ " declares " + owner.getSimpleName() + "." + name + ", which does not exist"));
	}

	private static void describe(Operation operation, String status, List<ApiCode> codes) {
		String described = codes.stream()
				.map(code -> "code=" + code.name() + " — " + code.getMessage())
				.collect(Collectors.joining("\n"));
		ApiResponse response = operation.getResponses().get(status);
		if (response == null) {
			operation.getResponses().addApiResponse(status, new ApiResponse()
					.description(described)
					.content(new Content().addMediaType(MEDIA_TYPE,
							new MediaType().schema(new Schema<>().$ref(REF)))));
			return;
		}
		String existing = response.getDescription();
		if (existing == null || existing.isBlank()) {
			response.setDescription(described);
		} else if (!existing.contains(described)) {
			response.setDescription(existing + "\n" + described);
		}
	}
}
