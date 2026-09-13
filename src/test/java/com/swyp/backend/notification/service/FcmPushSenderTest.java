package com.swyp.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class FcmPushSenderTest {

	private static final String BASE_URL = "https://fcm.googleapis.test";
	private static final String PROJECT_ID = "swyp-test";
	private static final String SEND_URL = BASE_URL + "/v1/projects/" + PROJECT_ID + "/messages:send";
	private static final String CHANNEL_ID = "swyp_default";
	private static final String DEVICE_TOKEN = "fcm-token-of-the-galaxy-s24";

	private static final PushSender.PushMessage MESSAGE = new PushSender.PushMessage(
		"새 찜이 들어왔어요",
		"망원동 주민님이 찜했어요.",
		Map.of("type", "NEW_HOLD_RECEIVED", "notificationId", "7"));

	private MockRestServiceServer server;
	private FcmPushSender sender;

	@BeforeEach
	void setUp() {
		sender = senderWith(unexpiredCredentials());
	}

	private FcmPushSender senderWith(GoogleCredentials credentials) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new FcmPushSender(
			builder, new ObjectMapper(), BASE_URL, PROJECT_ID, credentials, CHANNEL_ID);
	}

	private static GoogleCredentials unexpiredCredentials() {
		return GoogleCredentials.create(new AccessToken(
			"test-access-token", Date.from(Instant.now().plus(Duration.ofHours(1)))));
	}

	private void expectSend(org.springframework.test.web.client.response.DefaultResponseCreator response) {
		server.expect(requestTo(SEND_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-access-token"))
			.andRespond(response);
	}

	@Test
	void aDeliveredPushCarriesTheTrayTextAndTheDataTheAppRoutesOn() {
		server.expect(requestTo(SEND_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-access-token"))
			.andExpect(jsonPath("$.message.token").value(DEVICE_TOKEN))
			.andExpect(jsonPath("$.message.notification.title").value("새 찜이 들어왔어요"))
			.andExpect(jsonPath("$.message.notification.body").value("망원동 주민님이 찜했어요."))
			.andExpect(jsonPath("$.message.data.type").value("NEW_HOLD_RECEIVED"))
			.andExpect(jsonPath("$.message.data.notificationId").value("7"))
			.andExpect(jsonPath("$.message.android.priority").value("HIGH"))
			.andExpect(jsonPath("$.message.android.notification.channel_id").value(CHANNEL_ID))
			.andRespond(withSuccess("{\"name\":\"projects/swyp-test/messages/1\"}",
				MediaType.APPLICATION_JSON));

		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.DELIVERED);
		server.verify();
	}

	@Test
	void anUnregisteredTokenIsReportedAsGoneSoItCanBeDropped() {
		expectSend(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
				{"error":{"code":404,"status":"NOT_FOUND","details":[
					{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
					 "errorCode":"UNREGISTERED"}]}}"""));

		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.TOKEN_GONE);
		server.verify();
	}

	@Test
	void aTokenMintedByAnotherFirebaseProjectIsAlsoGone() {
		expectSend(withStatus(HttpStatus.FORBIDDEN)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
				{"error":{"code":403,"status":"PERMISSION_DENIED","details":[
					{"errorCode":"SENDER_ID_MISMATCH"}]}}"""));

		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.TOKEN_GONE);
	}

	@Test
	void aPayloadWeGotWrongIsRejectedWithoutBlamingTheDevice() {
		expectSend(withStatus(HttpStatus.BAD_REQUEST)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
				{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[
					{"errorCode":"INVALID_ARGUMENT"}]}}"""));

		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.REJECTED);
	}

	@Test
	void throttlingAndOutagesLeaveThePushRetryable() {
		expectSend(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.RETRYABLE);

		setUp();
		expectSend(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.RETRYABLE);
	}

	@Test
	void anErrorBodyWeCannotParseIsNotMistakenForADeadToken() {
		expectSend(withStatus(HttpStatus.BAD_REQUEST)
			.contentType(MediaType.TEXT_HTML)
			.body("<html>gateway said no</html>"));

		assertThat(sender.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.REJECTED);
	}

	@Test
	void withoutCredentialsNothingLeavesTheServer() {
		FcmPushSender unconfigured = senderWith(null);

		assertThat(unconfigured.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.DISABLED);
		server.verify();
	}

	@Test
	void keyMaterialWeCannotDecodeDisablesSendingInsteadOfBreakingStartup() {
		FcmPushSender misconfigured = new FcmPushSender(
			RestClient.builder(), new ObjectMapper(), BASE_URL, PROJECT_ID,
			"this-is-not-base64-json", CHANNEL_ID);

		assertThat(misconfigured.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.DISABLED);
	}

	@Test
	void anEmptyProjectIdDisablesSendingEvenWithAKeyPresent() {
		FcmPushSender unconfigured = new FcmPushSender(
			RestClient.builder(), new ObjectMapper(), BASE_URL, "",
			"eyJ0eXBlIjoic2VydmljZV9hY2NvdW50In0=", CHANNEL_ID);

		assertThat(unconfigured.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.DISABLED);
	}

	@Test
	void anAppWithoutAnAgreedChannelGetsNoChannelBlock() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		FcmPushSender channelless = new FcmPushSender(
			builder, new ObjectMapper(), BASE_URL, PROJECT_ID, unexpiredCredentials(), "");
		server.expect(requestTo(SEND_URL))
			.andExpect(jsonPath("$.message.android.priority").value("HIGH"))
			.andExpect(jsonPath("$.message.android.notification").doesNotExist())
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(channelless.send(DEVICE_TOKEN, MESSAGE)).isEqualTo(PushSender.Result.DELIVERED);
		server.verify();
	}
}
