package com.swyp.backend.shorts.service;

import tools.jackson.databind.JsonNode;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.shorts.exception.ShortsErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ShortsClient {

	private static final String TOKEN_HEADER = "X-Shorts-Token";

	private final RestClient restClient;
	private final String apiToken;

	public ShortsClient(
			RestClient.Builder builder,
			@Value("${shorts.base-url}") String baseUrl,
			@Value("${shorts.api-token:}") String apiToken,
			@Value("${shorts.read-timeout:30s}") Duration readTimeout) {
		var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(readTimeout);
		this.restClient = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
		this.apiToken = apiToken;
		if (apiToken.isBlank()) {
			log.info("No shorts.api-token configured — the pipeline service must be loopback-only");
		}
	}

	public JsonNode get(String path) {
		return exchange(() -> restClient.get().uri(path).headers(this::applyToken).retrieve().body(JsonNode.class));
	}

	public JsonNode post(String path, Object body) {
		return exchange(() -> {
			var request = restClient.post().uri(path).headers(this::applyToken);
			return (body == null ? request : request.body(body)).retrieve().body(JsonNode.class);
		});
	}

	public Resource download(String path) {
		return exchange(
			() -> restClient.get().uri(path).headers(this::applyToken).retrieve().body(Resource.class));
	}

	private void applyToken(org.springframework.http.HttpHeaders headers) {
		if (!apiToken.isBlank()) {
			headers.set(TOKEN_HEADER, apiToken);
		}
	}

	private <T> T exchange(java.util.function.Supplier<T> call) {
		try {
			return call.get();
		} catch (HttpClientErrorException e) {
			HttpStatusCode status = e.getStatusCode();
			log.warn("Shorts service rejected the request: {} {}", status, e.getResponseBodyAsString());
			throw new BusinessException(status.value() == 404
				? ShortsErrorCode.SHORTS_NOT_FOUND
				: ShortsErrorCode.SHORTS_REQUEST_REJECTED);
		} catch (RestClientException e) {
			log.warn("Shorts service is unreachable at request time", e);
			throw new BusinessException(ShortsErrorCode.SHORTS_UNAVAILABLE);
		}
	}

	public static Map<String, Object> withAdmin(Map<String, Object> body, Long adminId) {
		var copy = new java.util.LinkedHashMap<String, Object>(body);
		copy.put("adminId", adminId);
		return copy;
	}
}
