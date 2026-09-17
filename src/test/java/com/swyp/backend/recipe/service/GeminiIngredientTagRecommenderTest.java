package com.swyp.backend.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiIngredientTagRecommenderTest {

	private static final String BASE_URL = "https://gemini.googleapis.test";
	private static final List<String> TAGS = List.of("복숭아", "요거트", "달걀");

	private MockRestServiceServer server;

	private GeminiIngredientTagRecommender recommenderWith(String apiKey, String model) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new GeminiIngredientTagRecommender(builder, new ObjectMapper(), BASE_URL, apiKey, model);
	}

	private static String answerWriting(String text) {
		return """
			{"candidates":[{"content":{"parts":[{"text":%s}]}}]}""".formatted(new ObjectMapper().writeValueAsString(text));
	}

	@Test
	void recommendTagNames_sendsTheTagListAndReadsTheJsonArrayTheModelWrote() {
		GeminiIngredientTagRecommender recommender = recommenderWith("test-key", "gemini-test");
		server.expect(requestTo(BASE_URL + "/v1beta/models/gemini-test:generateContent"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", "test-key"))
			.andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
			.andExpect(jsonPath("$.generationConfig.responseSchema.type").value("ARRAY"))
			.andExpect(request -> assertThat(request.getBody().toString())
				.contains("복숭아 4입")
				.contains("복숭아, 요거트, 달걀"))
			.andRespond(withSuccess(answerWriting("[\"복숭아\", \"요거트\"]"), MediaType.APPLICATION_JSON));

		assertThat(recommender.recommendTagNames("복숭아 4입", TAGS, 5)).containsExactly("복숭아", "요거트");
		server.verify();
	}

	@Test
	void recommendTagNames_withABlankModel_fallsBackToTheDefaultModel() {
		GeminiIngredientTagRecommender recommender = recommenderWith("test-key", " ");
		server.expect(requestTo(BASE_URL + "/v1beta/models/"
				+ GeminiIngredientTagRecommender.DEFAULT_MODEL + ":generateContent"))
			.andRespond(withSuccess(answerWriting("[]"), MediaType.APPLICATION_JSON));

		assertThat(recommender.recommendTagNames("복숭아", TAGS, 5)).isEmpty();
		server.verify();
	}

	@Test
	void recommendTagNames_whenTheModelWritesBrokenJson_isEmptyRatherThanAnError() {
		GeminiIngredientTagRecommender recommender = recommenderWith("test-key", "gemini-test");
		server.expect(requestTo(BASE_URL + "/v1beta/models/gemini-test:generateContent"))
			.andRespond(withSuccess(answerWriting("[\"복숭아\", \"요거"), MediaType.APPLICATION_JSON));

		assertThat(recommender.recommendTagNames("복숭아", TAGS, 5)).isEmpty();
	}

	@Test
	void recommendTagNames_whenGeminiFails_isEmptyRatherThanAnError() {
		GeminiIngredientTagRecommender recommender = recommenderWith("test-key", "gemini-test");
		server.expect(requestTo(BASE_URL + "/v1beta/models/gemini-test:generateContent"))
			.andRespond(withServerError());

		assertThat(recommender.recommendTagNames("복숭아", TAGS, 5)).isEmpty();
	}

	@Test
	void recommendTagNames_withoutAKey_neverCallsGemini() {
		GeminiIngredientTagRecommender recommender = recommenderWith("", "gemini-test");

		assertThat(recommender.recommendTagNames("복숭아", TAGS, 5)).isEmpty();
		server.verify();
	}
}
