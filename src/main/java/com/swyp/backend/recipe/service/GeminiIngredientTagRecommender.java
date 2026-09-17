package com.swyp.backend.recipe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiIngredientTagRecommender implements IngredientTagRecommender {

	static final String DEFAULT_MODEL = "gemini-3.8-flash";

	private static final String GENERATE_CONTENT_PATH = "/v1beta/models/{model}:generateContent";
	private static final String API_KEY_HEADER = "x-goog-api-key";

	private static final String PROMPT = """
			너는 한국 동네 가게의 마감 임박 상품에 식자재 태그를 다는 도우미다.
			상품명: "%s"

			아래 태그 목록에서 이 상품의 재료를 가리키는 태그를 최대 %d개 골라라.
			규칙:
			- 목록에 있는 태그만, 목록에 적힌 글자 그대로 쓴다.
			- 상품에 실제로 들어가는 재료만 고르고, 관련이 큰 것부터 쓴다.
			- 간장·참기름 같은 양념은 상품 자체가 그 양념일 때만 고른다.
			- 식자재로 보이지 않거나 맞는 태그가 없으면 빈 배열을 준다.

			태그 목록: %s
			""";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String model;

	public GeminiIngredientTagRecommender(
			RestClient.Builder builder,
			ObjectMapper objectMapper,
			@Value("${gemini.api-base-url}") String baseUrl,
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.model:}") String model) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model.isBlank() ? DEFAULT_MODEL : model.strip();
		if (apiKey.isBlank()) {
			log.warn("No Gemini API key configured — ingredient tag recommendation stays empty "
				+ "until one is set");
		}
	}

	@Override
	public List<String> recommendTagNames(String productName, List<String> tagNames, int limit) {
		if (apiKey.isBlank()) {
			return List.of();
		}
		try {
			JsonNode answer = restClient.post()
				.uri(GENERATE_CONTENT_PATH, Map.of("model", model))
				.header(API_KEY_HEADER, apiKey)
				.body(requestBody(productName, tagNames, limit))
				.retrieve()
				.body(JsonNode.class);
			return tagNamesOf(answer);
		} catch (RestClientException | JacksonException e) {
			log.warn("Gemini did not answer the tag recommendation for {}", productName, e);
			return List.of();
		}
	}

	private static Map<String, Object> requestBody(String productName, List<String> tagNames, int limit) {
		String prompt = PROMPT.formatted(productName, limit, String.join(", ", tagNames));
		return Map.of(
			"contents", List.of(Map.of(
				"parts", List.of(Map.of("text", prompt)))),
			"generationConfig", Map.of(
				"responseMimeType", "application/json",
				"responseSchema", Map.of(
					"type", "ARRAY",
					"items", Map.of("type", "STRING"))));
	}

	private List<String> tagNamesOf(JsonNode answer) {
		if (answer == null) {
			return List.of();
		}
		JsonNode text = answer.path("candidates").path(0).path("content").path("parts").path(0).path("text");
		if (!text.isString()) {
			return List.of();
		}
		JsonNode written = objectMapper.readTree(text.asString());
		if (!written.isArray()) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		written.forEach(node -> {
			if (node.isString()) {
				names.add(node.asString());
			}
		});
		return List.copyOf(names);
	}
}
