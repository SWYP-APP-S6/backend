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
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KakaoRestOauthClient implements KakaoOauthClient {

	private static final String TOKEN_INFO_PATH = "/v1/user/access_token_info";
	private static final String USER_ME_PATH = "/v2/user/me";

	private final RestClient restClient;
	private final Map<UserRole, Long> appIds;

	public KakaoRestOauthClient(
			RestClient.Builder builder,
			@Value("${kakao.api-base-url}") String apiBaseUrl,
			@Value("${kakao.consumer-app-id}") long consumerAppId,
			@Value("${kakao.owner-app-id}") long ownerAppId) {
		this.restClient = builder.baseUrl(apiBaseUrl).build();
		this.appIds = new EnumMap<>(Map.of(UserRole.CONSUMER, consumerAppId, UserRole.OWNER, ownerAppId));
		this.appIds.forEach((role, appId) -> {
			if (appId <= 0) {
				log.warn("No Kakao app id configured for {} — those logins are rejected until one is set", role);
			}
		});
		if (consumerAppId > 0 && consumerAppId == ownerAppId) {
			log.warn("Both apps share Kakao app id {} — a token from either app passes both endpoints",
				consumerAppId);
		}
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

	private <T> T get(String path, String kakaoAccessToken, Class<T> responseType) {
		try {
			return restClient.get()
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
}
