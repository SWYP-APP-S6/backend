package com.swyp.backend.recipe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiIngredientTagRecommender implements IngredientTagRecommender {

	private static final String GENERATE_CONTENT_PATH = "/v1beta/models/{model}:generateContent";

	private static final String PROMPT = """
			너는 한국 동네 가게의 마감 임박 상품에 식자재 태그를 다는 도우미다.
			상품명: "%s"

			이 상품을 찾는 손님이 떠올릴 만한 한국어 식자재 태그를 최대 %d개 제안해라.
			규칙:
			- 상품 자체를 가리키는 말과 그 상위 분류를 함께 넣는다. 예: "복숭아" -> ["복숭아", "과일"]
			- 한 태그는 공백 없는 한국어 낱말로 짧게 쓴다. 수량·단위·포장 표현은 넣지 않는다.
			- 브랜드명, 가게 이름, 형용사는 넣지 않는다.
			- 상품명이 식자재로 보이지 않으면 빈 배열을 준다.
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
			@Value("${gemini.model}") String model) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
		this.model = model;
		if (apiKey.isBlank()) {
			log.warn("No Gemini API key configured — ingredient tag recommendation stays empty "
				+ "until one is set");
		}
	}

	@Override
	public List<String> recommendTagNames(String productName, int limit) {
		if (apiKey.isBlank()) {
			return List.of();
		}
		try {
			JsonNode answer = restClient.post()
				.uri(uriBuilder -> uriBuilder
					.path(GENERATE_CONTENT_PATH)
					.queryParam("key", apiKey)
					.build(Map.of("model", model)))
				.body(requestBody(productName, limit))
				.retrieve()
				.body(JsonNode.class);
			return tagNamesOf(answer);
		} catch (RestClientException e) {
			log.warn("Gemini did not answer the tag recommendation for {}", productName, e);
			return List.of();
		}
	}

	private static Map<String, Object> requestBody(String productName, int limit) {
		return Map.of(
			"contents", List.of(Map.of(
				"parts", List.of(Map.of("text", PROMPT.formatted(productName, limit))))),
			"generationConfig", Map.of(
				"response_mime_type", "application/json",
				"response_schema", Map.of(
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
		List<String> names = new ArrayList<>();
		JsonNode written = objectMapper.readTree(text.asString());
		if (!written.isArray()) {
			return List.of();
		}
		written.forEach(node -> {
			if (node.isString()) {
				names.add(node.asString());
			}
		});
		return List.copyOf(names);
	}
}
