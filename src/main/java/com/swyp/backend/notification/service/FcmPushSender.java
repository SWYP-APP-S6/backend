package com.swyp.backend.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class FcmPushSender implements PushSender {

	private static final String MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
	private static final String SEND_PATH = "/v1/projects/{projectId}/messages:send";
	private static final String UNREGISTERED = "UNREGISTERED";
	private static final String SENDER_ID_MISMATCH = "SENDER_ID_MISMATCH";
	private static final int TOO_MANY_REQUESTS = 429;

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final GoogleCredentials credentials;
	private final String projectId;
	private final String androidChannelId;

	@Autowired
	public FcmPushSender(
			RestClient.Builder builder,
			ObjectMapper objectMapper,
			@Value("${fcm.base-url}") String baseUrl,
			@Value("${fcm.project-id:}") String projectId,
			@Value("${fcm.credentials-base64:}") String credentialsBase64,
			@Value("${fcm.android-channel-id:}") String androidChannelId) {
		this(builder, objectMapper, baseUrl, projectId,
				loadCredentials(projectId, credentialsBase64), androidChannelId);
	}

	FcmPushSender(
			RestClient.Builder builder,
			ObjectMapper objectMapper,
			String baseUrl,
			String projectId,
			GoogleCredentials credentials,
			String androidChannelId) {
		this.restClient = builder.clone().baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
		this.projectId = projectId;
		this.androidChannelId = androidChannelId;
		this.credentials = credentials;
	}

	@Override
	public Result send(String fcmToken, PushMessage message) {
		if (credentials == null) {
			return Result.DISABLED;
		}
		try {
			restClient.post()
				.uri(SEND_PATH, projectId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(payloadOf(fcmToken, message))
				.retrieve()
				.toBodilessEntity();
			return Result.DELIVERED;
		} catch (HttpClientErrorException e) {
			return classify(e);
		} catch (HttpServerErrorException e) {
			log.warn("FCM is unavailable ({}) -- the push stays pending", e.getStatusCode());
			return Result.RETRYABLE;
		} catch (RestClientException | IOException e) {
			log.warn("FCM call failed -- the push stays pending", e);
			return Result.RETRYABLE;
		}
	}

	private static GoogleCredentials loadCredentials(String projectId, String credentialsBase64) {
		if (projectId.isBlank() || credentialsBase64.isBlank()) {
			log.warn("FCM is not configured (fcm.project-id / fcm.credentials-base64) -- "
				+ "notifications reach the in-app inbox only");
			return null;
		}
		try (InputStream credentialsJson = new ByteArrayInputStream(
				Base64.getDecoder().decode(credentialsBase64.strip()))) {
			return GoogleCredentials.fromStream(credentialsJson).createScoped(MESSAGING_SCOPE);
		} catch (IOException | RuntimeException e) {
			log.error("fcm.credentials-base64 is not a readable service account key ({}) -- "
				+ "notifications reach the in-app inbox only. The cause is withheld because a "
				+ "parser reports the text around the failure, which here is the private key.",
				e.getClass().getSimpleName());
			return null;
		}
	}

	private String accessToken() throws IOException {
		credentials.refreshIfExpired();
		return credentials.getAccessToken().getTokenValue();
	}

	private Result classify(HttpClientErrorException e) {
		String errorCode = errorCodeOf(e.getResponseBodyAsString());
		if (UNREGISTERED.equals(errorCode) || SENDER_ID_MISMATCH.equals(errorCode)) {
			log.info("FCM no longer accepts a device token ({}) -- dropping it", errorCode);
			return Result.TOKEN_GONE;
		}
		if (e.getStatusCode().value() == TOO_MANY_REQUESTS) {
			log.warn("FCM throttled the push -- it stays pending");
			return Result.RETRYABLE;
		}
		log.warn("FCM rejected the push: {} {}", e.getStatusCode(), errorCode);
		return Result.REJECTED;
	}

	private String errorCodeOf(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return null;
		}
		try {
			for (JsonNode detail : objectMapper.readTree(responseBody).at("/error/details")) {
				JsonNode errorCode = detail.get("errorCode");
				if (errorCode != null && !errorCode.isNull()) {
					return errorCode.asString();
				}
			}
		} catch (RuntimeException e) {
			log.debug("FCM error body was not the expected shape");
		}
		return null;
	}

	private Map<String, Object> payloadOf(String fcmToken, PushMessage message) {
		Map<String, Object> android = new LinkedHashMap<>();
		android.put("priority", "HIGH");
		if (!androidChannelId.isBlank()) {
			android.put("notification", Map.of("channel_id", androidChannelId));
		}

		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("token", fcmToken);
		envelope.put("notification", Map.of("title", message.title(), "body", message.body()));
		envelope.put("data", message.data());
		envelope.put("android", android);
		return Map.of("message", envelope);
	}
}
