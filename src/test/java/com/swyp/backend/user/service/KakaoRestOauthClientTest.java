package com.swyp.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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
import org.springframework.web.client.RestClient;

class KakaoRestOauthClientTest {

	private static final long CONSUMER_APP_ID = 123456L;
	private static final long OWNER_APP_ID = 654321L;
	private static final String BASE_URL = "https://kapi.kakao.test";
	private static final String TOKEN_INFO_URL = BASE_URL + "/v1/user/access_token_info";
	private static final String USER_ME_URL = BASE_URL + "/v2/user/me";
	private static final String KAKAO_TOKEN = "kakao-access-token";

	private MockRestServiceServer server;
	private KakaoRestOauthClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoRestOauthClient(builder, BASE_URL, CONSUMER_APP_ID, OWNER_APP_ID);
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
}
