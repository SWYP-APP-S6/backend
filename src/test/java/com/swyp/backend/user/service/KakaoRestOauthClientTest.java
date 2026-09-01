package com.swyp.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KakaoRestOauthClientTest {

	private static final long CONSUMER_APP_ID = 123456L;
	private static final long OWNER_APP_ID = 654321L;
	private static final String CONSUMER_REST_API_KEY = "consumer-rest-api-key";
	private static final String BASE_URL = "https://kapi.kakao.test";
	private static final String AUTH_BASE_URL = "https://kauth.kakao.test";
	private static final String TOKEN_INFO_URL = BASE_URL + "/v1/user/access_token_info";
	private static final String USER_ME_URL = BASE_URL + "/v2/user/me";
	private static final String TOKEN_URL = AUTH_BASE_URL + "/oauth/token";
	private static final String KAKAO_TOKEN = "kakao-access-token";

	private MockRestServiceServer server;
	private KakaoRestOauthClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoRestOauthClient(
			builder, BASE_URL, AUTH_BASE_URL, CONSUMER_APP_ID, OWNER_APP_ID, CONSUMER_REST_API_KEY, "");
	}

	private void expectTokenInfo(String body) {
		server.expect(requestTo(TOKEN_INFO_URL))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer " + KAKAO_TOKEN))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	@Test
	void fetchIdentity_returnsProviderIdAndNickname() {
		expectTokenInfo("""
			{"id":4102938475,"expires_in":21599,"app_id":123456}""");
		server.expect(requestTo(USER_ME_URL))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer " + KAKAO_TOKEN))
			.andRespond(withSuccess("""
				{"id":4102938475,"kakao_account":{"profile":{"nickname":"성문"}}}""",
				MediaType.APPLICATION_JSON));

		KakaoOauthClient.Identity identity = client.fetchIdentity(UserRole.CONSUMER, KAKAO_TOKEN);

		assertThat(identity.providerId()).isEqualTo("4102938475");
		assertThat(identity.nickname()).isEqualTo("성문");
		server.verify();
	}

	@Test
	void fetchIdentity_rejectsTokenIssuedForAnotherService() {
		expectTokenInfo("""
			{"id":4102938475,"expires_in":21599,"app_id":999999}""");

		BusinessException thrown = catchThrowableOfType(
			BusinessException.class, () -> client.fetchIdentity(UserRole.CONSUMER, KAKAO_TOKEN));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
		server.verify();
	}

	@Test
	void fetchIdentity_rejectsAConsumerAppTokenOnTheOwnerApp() {
		expectTokenInfo("""
			{"id":4102938475,"expires_in":21599,"app_id":123456}""");

		BusinessException thrown = catchThrowableOfType(
			BusinessException.class, () -> client.fetchIdentity(UserRole.OWNER, KAKAO_TOKEN));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
		server.verify();
	}

	@Test
	void fetchIdentity_toleratesMissingProfileConsent() {
		expectTokenInfo("""
			{"id":4102938475,"expires_in":21599,"app_id":123456}""");
		server.expect(requestTo(USER_ME_URL))
			.andRespond(withSuccess("""
				{"id":4102938475,"kakao_account":{}}""", MediaType.APPLICATION_JSON));

		KakaoOauthClient.Identity identity = client.fetchIdentity(UserRole.CONSUMER, KAKAO_TOKEN);

		assertThat(identity.providerId()).isEqualTo("4102938475");
		assertThat(identity.nickname()).isNull();
	}

	@Test
	void fetchIdentity_mapsKakaoRejectionToInvalidOauthToken() {
		server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withUnauthorizedRequest());

		BusinessException thrown = catchThrowableOfType(
			BusinessException.class, () -> client.fetchIdentity(UserRole.CONSUMER, KAKAO_TOKEN));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
	}

	@Test
	void fetchIdentity_mapsKakaoOutageToProviderUnavailable() {
		server.expect(requestTo(TOKEN_INFO_URL)).andRespond(withServerError());

		BusinessException thrown = catchThrowableOfType(
			BusinessException.class, () -> client.fetchIdentity(UserRole.CONSUMER, KAKAO_TOKEN));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
	}

	@Test
	void exchangeAuthorizationCode_returnsAccessToken() {
		server.expect(requestTo(TOKEN_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formData(formOf(
				"grant_type", "authorization_code",
				"client_id", CONSUMER_REST_API_KEY,
				"redirect_uri", "http://localhost:5173/kakao-test",
				"code", "auth-code-123")))
			.andRespond(withSuccess("""
				{"access_token":"kakao-issued-token","token_type":"bearer","refresh_token":"r","expires_in":21599}""",
				MediaType.APPLICATION_JSON));

		String accessToken = client.exchangeAuthorizationCode(
			UserRole.CONSUMER, "auth-code-123", "http://localhost:5173/kakao-test");

		assertThat(accessToken).isEqualTo("kakao-issued-token");
		server.verify();
	}

	@Test
	void exchangeAuthorizationCode_mapsInvalidCodeToInvalidOauthToken() {
		server.expect(requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());

		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> client.exchangeAuthorizationCode(UserRole.CONSUMER, "bad-code", "http://localhost:5173/kakao-test"));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
	}

	@Test
	void exchangeAuthorizationCode_mapsKakaoOutageToProviderUnavailable() {
		server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> client.exchangeAuthorizationCode(UserRole.CONSUMER, "auth-code-123",
				"http://localhost:5173/kakao-test"));

		assertThat(thrown.getCode()).isEqualTo(UserAuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
	}

	private static MultiValueMap<String, String> formOf(String... keyValuePairs) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		for (int i = 0; i < keyValuePairs.length; i += 2) {
			form.add(keyValuePairs[i], keyValuePairs[i + 1]);
		}
		return form;
	}
}
