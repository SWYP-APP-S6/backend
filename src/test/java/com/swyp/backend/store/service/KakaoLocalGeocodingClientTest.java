package com.swyp.backend.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.store.exception.StoreErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocalGeocodingClientTest {

	private static final String BASE_URL = "https://dapi.kakao.test";
	private static final String REST_API_KEY = "local-rest-api-key";
	private static final String ADDRESS = "서울특별시 강남구 역삼로 123";

	private MockRestServiceServer server;
	private KakaoLocalGeocodingClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoLocalGeocodingClient(builder, BASE_URL, REST_API_KEY);
	}

	@Test
	void geocode_returnsCoordinatesFromTheFirstDocument() {
		server.expect(requestToUriTemplate(BASE_URL + "/v2/local/search/address.json?query={query}", ADDRESS))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "KakaoAK " + REST_API_KEY))
			.andRespond(withSuccess("""
				{"documents":[{"address_name":"%s","x":"127.036500","y":"37.500600"}]}""".formatted(ADDRESS),
				MediaType.APPLICATION_JSON));

		GeocodingClient.Coordinates coordinates = client.geocode(ADDRESS);

		assertThat(coordinates.latitude()).isEqualByComparingTo(new BigDecimal("37.500600"));
		assertThat(coordinates.longitude()).isEqualByComparingTo(new BigDecimal("127.036500"));
		server.verify();
	}

	@Test
	void geocode_roundsCoordinatesToSixDecimalPlaces() {
		server.expect(requestToUriTemplate(BASE_URL + "/v2/local/search/address.json?query={query}", ADDRESS))
			.andRespond(withSuccess("""
				{"documents":[{"x":"127.03650049","y":"37.50060051"}]}""", MediaType.APPLICATION_JSON));

		GeocodingClient.Coordinates coordinates = client.geocode(ADDRESS);

		assertThat(coordinates.latitude()).isEqualByComparingTo(new BigDecimal("37.500601"));
		assertThat(coordinates.longitude()).isEqualByComparingTo(new BigDecimal("127.036500"));
	}

	@Test
	void geocode_withNoMatchingAddress_isRejected() {
		server.expect(requestToUriTemplate(BASE_URL + "/v2/local/search/address.json?query={query}", ADDRESS))
			.andRespond(withSuccess("""
				{"documents":[]}""", MediaType.APPLICATION_JSON));

		BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> client.geocode(ADDRESS));

		assertThat(thrown.getCode()).isEqualTo(StoreErrorCode.GEOCODING_FAILED);
	}

	@Test
	void geocode_mapsKakaoRejectionToGeocodingFailed() {
		server.expect(requestToUriTemplate(BASE_URL + "/v2/local/search/address.json?query={query}", ADDRESS))
			.andRespond(withUnauthorizedRequest());

		BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> client.geocode(ADDRESS));

		assertThat(thrown.getCode()).isEqualTo(StoreErrorCode.GEOCODING_FAILED);
	}

	@Test
	void geocode_mapsKakaoOutageToGeocodingFailed() {
		server.expect(requestToUriTemplate(BASE_URL + "/v2/local/search/address.json?query={query}", ADDRESS))
			.andRespond(withServerError());

		BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> client.geocode(ADDRESS));

		assertThat(thrown.getCode()).isEqualTo(StoreErrorCode.GEOCODING_FAILED);
	}
}
