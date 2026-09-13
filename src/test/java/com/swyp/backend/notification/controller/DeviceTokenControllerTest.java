package com.swyp.backend.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.notification.repository.UserDeviceTokenRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class DeviceTokenControllerTest {

	private static final String PATH = "/notifications/device-tokens";
	private static final String TOKEN = "fcm-token-of-the-galaxy-s24";

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserDeviceTokenRepository userDeviceTokenRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private User consumer;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = newUser(UserRole.CONSUMER, "망원동 주민");
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void registeringATokenMakesItReachableForThatUser() throws Exception {
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByUserId(consumer.getId()))
			.singleElement()
			.satisfies(deviceToken -> assertThat(deviceToken.getFcmToken()).isEqualTo(TOKEN));
	}

	@Test
	void registeringTheSameTokenTwiceKeepsOneRow() throws Exception {
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());
		Instant firstUse = userDeviceTokenRepository.findByFcmToken(TOKEN).orElseThrow().getLastUsedAt();

		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByUserId(consumer.getId())).hasSize(1);
		assertThat(userDeviceTokenRepository.findByFcmToken(TOKEN).orElseThrow().getLastUsedAt())
			.isAfterOrEqualTo(firstUse);
	}

	@Test
	void aTokenFollowsTheUserWhoRegisteredItLast() throws Exception {
		User nextOwnerOfThePhone = newUser(UserRole.OWNER, "청과마을사장");
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());

		register(nextOwnerOfThePhone, "ANDROID", TOKEN).andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByUserId(consumer.getId())).isEmpty();
		assertThat(userDeviceTokenRepository.findByUserId(nextOwnerOfThePhone.getId()))
			.singleElement()
			.satisfies(deviceToken -> assertThat(deviceToken.getFcmToken()).isEqualTo(TOKEN));
	}

	@Test
	void oneUserCanCarryTokensOfSeveralDevices() throws Exception {
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());
		register(consumer, "IOS", "fcm-token-of-the-iphone").andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByUserId(consumer.getId())).hasSize(2);
	}

	@Test
	void unregisteringStopsThePushesForThatDevice() throws Exception {
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());

		mockMvc.perform(delete(PATH)
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fcmToken\":\"" + TOKEN + "\"}"))
			.andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByFcmToken(TOKEN)).isEmpty();
	}

	@Test
	void unregisteringSomeoneElsesTokenLeavesItAlone() throws Exception {
		register(consumer, "ANDROID", TOKEN).andExpect(status().isOk());
		User stranger = newUser(UserRole.CONSUMER, "남");

		mockMvc.perform(delete(PATH)
				.header("Authorization", bearer(stranger))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fcmToken\":\"" + TOKEN + "\"}"))
			.andExpect(status().isOk());

		assertThat(userDeviceTokenRepository.findByFcmToken(TOKEN)).isPresent();
	}

	@Test
	void aGuestHasNoDeviceToRegister() throws Exception {
		mockMvc.perform(post(PATH)
				.header("Authorization",
					"Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"ANDROID\",\"fcmToken\":\"" + TOKEN + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

		assertThat(userDeviceTokenRepository.findByFcmToken(TOKEN)).isEmpty();
	}

	@Test
	void aTokenLongerThanTheColumnIsRejectedBeforeItReachesTheDatabase() throws Exception {
		mockMvc.perform(post(PATH)
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"ANDROID\",\"fcmToken\":\"" + "x".repeat(256) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.fcmToken").isNotEmpty());
	}

	@Test
	void aRegistrationWithoutAPlatformIsRejected() throws Exception {
		mockMvc.perform(post(PATH)
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fcmToken\":\"" + TOKEN + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	private ResultActions register(
			User user, String platform, String fcmToken) throws Exception {
		return mockMvc.perform(post(PATH)
			.header("Authorization", bearer(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"platform\":\"" + platform + "\",\"fcmToken\":\"" + fcmToken + "\"}"));
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
			TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private User newUser(UserRole role, String nickname) {
		return userRepository.saveAndFlush(new User(role, nickname, null, false, Instant.now()));
	}
}
