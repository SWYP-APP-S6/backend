package com.swyp.backend.store.service;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KakaoLocalGeocodingClient implements GeocodingClient {

	private static final String ADDRESS_SEARCH_PATH = "/v2/local/search/address.json";
	private static final int COORDINATE_SCALE = 6;

	private final RestClient restClient;
	private final String restApiKey;

	public KakaoLocalGeocodingClient(
			RestClient.Builder builder,
			@Value("${kakao.local-api-base-url}") String baseUrl,
			@Value("${kakao.local-rest-api-key:}") String restApiKey) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.restApiKey = restApiKey;
		if (restApiKey.isBlank()) {
			log.warn("No Kakao local REST API key configured — store registration geocoding "
				+ "is rejected until one is set");
		}
	}

	@Override
	public Coordinates geocode(String address) {
		try {
			AddressSearchResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path(ADDRESS_SEARCH_PATH).queryParam("query", address).build())
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
				.retrieve()
				.body(AddressSearchResponse.class);
			if (response == null || response.documents() == null || response.documents().isEmpty()) {
				log.warn("Kakao local API found no coordinates for the given address");
				throw new BusinessException(StoreErrorCode.GEOCODING_FAILED);
			}
			Document document = response.documents().getFirst();
			return new Coordinates(
				scale(document.y()),
				scale(document.x()));
		} catch (HttpClientErrorException e) {
			log.warn("Kakao local API rejected the geocoding request: {}", e.getStatusCode());
			throw new BusinessException(StoreErrorCode.GEOCODING_UNAVAILABLE);
		} catch (RestClientException e) {
			log.error("Kakao local API call failed", e);
			throw new BusinessException(StoreErrorCode.GEOCODING_UNAVAILABLE);
		}
	}

	private static BigDecimal scale(String value) {
		return new BigDecimal(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}

	record AddressSearchResponse(List<Document> documents) {
	}

	record Document(String x, String y) {
	}
}
