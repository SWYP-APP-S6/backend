package com.swyp.backend.user.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KakaoRestOauthClient implements KakaoOauthClient {

	private static final String TOKEN_INFO_PATH = "/v1/user/access_token_info";
	private static final String USER_ME_PATH = "/v2/user/me";
	private static final String TOKEN_PATH = "/oauth/token";

	private final RestClient apiClient;
	private final RestClient authClient;
	private final Map<UserRole, Long> appIds;
	private final Map<UserRole, String> restApiKeys;

	public KakaoRestOauthClient(
			RestClient.Builder builder,
			@Value("${kakao.api-base-url}") String apiBaseUrl,
			@Value("${kakao.auth-base-url}") String authBaseUrl,
			@Value("${kakao.consumer-app-id}") long consumerAppId,
			@Value("${kakao.owner-app-id}") long ownerAppId,
			@Value("${kakao.consumer-rest-api-key:}") String consumerRestApiKey,
			@Value("${kakao.owner-rest-api-key:}") String ownerRestApiKey) {
		this.apiClient = builder.clone().baseUrl(apiBaseUrl).build();
		this.authClient = builder.clone().baseUrl(authBaseUrl).build();
		this.appIds = new EnumMap<>(Map.of(UserRole.CONSUMER, consumerAppId, UserRole.OWNER, ownerAppId));
		this.restApiKeys = new EnumMap<>(
			Map.of(UserRole.CONSUMER, consumerRestApiKey, UserRole.OWNER, ownerRestApiKey));
		this.appIds.forEach((role, appId) -> {
			if (appId <= 0) {
				log.warn("No Kakao app id configured for {} — those logins are rejected until one is set", role);
			}
		});
		if (consumerAppId > 0 && consumerAppId == ownerAppId) {
			log.warn("Both apps share Kakao app id {} — a token from either app passes both endpoints",
				consumerAppId);
		}
		this.restApiKeys.forEach((role, key) -> {
			if (key.isBlank()) {
				log.warn("No Kakao REST API key configured for {} — authorization code exchange is rejected "
					+ "until one is set (the kakaoAccessToken login endpoints are unaffected)", role);
			}
		});
	}

	@Override
	public Identity fetchIdentity(UserRole role, String kakaoAccessToken) {
		long expectedAppId = appIds.get(role);
		TokenInfo tokenInfo = get(TOKEN_INFO_PATH, kakaoAccessToken, TokenInfo.class);
		if (tokenInfo == null || tokenInfo.id() == null || tokenInfo.appId() == null
				|| tokenInfo.appId() != expectedAppId) {
			log.warn("Rejected Kakao token issued for app {} on the {} endpoint (expected app {})",
				tokenInfo == null ? null : tokenInfo.appId(), role, expectedAppId);
			throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
		}
		UserMe userMe = get(USER_ME_PATH, kakaoAccessToken, UserMe.class);
		return new Identity(String.valueOf(tokenInfo.id()), userMe == null ? null : userMe.nickname());
	}

	@Override
	public String exchangeAuthorizationCode(UserRole role, String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", restApiKeys.get(role));
		form.add("redirect_uri", redirectUri);
		form.add("code", code);
		try {
			TokenExchangeResult result = authClient.post()
				.uri(TOKEN_PATH)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenExchangeResult.class);
			if (result == null || result.accessToken() == null) {
				throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
			}
			return result.accessToken();
		} catch (HttpClientErrorException e) {
			log.warn("Kakao rejected the authorization code exchange for {}: {}", role, e.getStatusCode());
			throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
		} catch (RestClientException e) {
			log.error("Kakao token exchange failed for {}", role, e);
			throw new BusinessException(UserAuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
		}
	}

	private <T> T get(String path, String kakaoAccessToken, Class<T> responseType) {
		try {
			return apiClient.get()
				.uri(path)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
				.retrieve()
				.body(responseType);
		} catch (HttpClientErrorException e) {
			log.warn("Kakao rejected the request to {}: {}", path, e.getStatusCode());
			throw new BusinessException(UserAuthErrorCode.INVALID_OAUTH_TOKEN);
		} catch (RestClientException e) {
			log.error("Kakao call to {} failed", path, e);
			throw new BusinessException(UserAuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
		}
	}

	record TokenInfo(Long id, @JsonProperty("app_id") Long appId) {
	}

	record UserMe(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

		String nickname() {
			if (kakaoAccount == null || kakaoAccount.profile() == null) {
				return null;
			}
			return kakaoAccount.profile().nickname();
		}
	}

	record KakaoAccount(Profile profile) {
	}

	record Profile(String nickname) {
	}

	record TokenExchangeResult(@JsonProperty("access_token") String accessToken) {
	}
}
