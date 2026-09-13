package com.swyp.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserLocationRepository;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class MyLocationControllerTest {

	private static final String MANGWON = """
			{"regionName":"서울특별시 마포구 망원동","latitude":37.556000,"longitude":126.901000}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	UserLocationRepository userLocationRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private User consumer() {
		return userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "망원동 주민", null, false, Instant.now()));
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
				TokenRealm.USER, user.getId(), user.getRole().name());
	}

	@Test
	void aUserWhoNeverSetAPlaceGetsAnEmptyAnswerRatherThanAnError() throws Exception {
		mockMvc.perform(get("/users/me/location").header("Authorization", bearer(consumer())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.location").doesNotExist());
	}

	@Test
	void settingThePlaceMakesItReadBack() throws Exception {
		User user = consumer();

		mockMvc.perform(put("/users/me/location")
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(MANGWON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.location.regionName").value("서울특별시 마포구 망원동"))
			.andExpect(jsonPath("$.data.location.latitude").value(37.556))
			.andExpect(jsonPath("$.data.location.updatedAt").isNotEmpty());

		mockMvc.perform(get("/users/me/location").header("Authorization", bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.location.regionName").value("서울특별시 마포구 망원동"));
	}

	@Test
	void settingItAgainMovesTheSameRowRatherThanAddingOne() throws Exception {
		User user = consumer();

		mockMvc.perform(put("/users/me/location")
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(MANGWON))
			.andExpect(status().isOk());
		mockMvc.perform(put("/users/me/location")
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"regionName":"서울특별시 강남구 역삼동","latitude":37.500600,"longitude":127.036500}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.location.regionName").value("서울특별시 강남구 역삼동"));

		assertThat(userLocationRepository.findByUserId(user.getId()).orElseThrow().getRegionName())
			.isEqualTo("서울특별시 강남구 역삼동");
		assertThat(userLocationRepository.count()).isEqualTo(1);
	}

	@Test
	void aPlaceWithoutANameIsRejected() throws Exception {
		mockMvc.perform(put("/users/me/location")
				.header("Authorization", bearer(consumer()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"regionName":" ","latitude":37.556000,"longitude":126.901000}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aCoordinateOffTheGlobeIsRejected() throws Exception {
		mockMvc.perform(put("/users/me/location")
				.header("Authorization", bearer(consumer()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"regionName":"어딘가","latitude":99.000000,"longitude":126.901000}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.latitude").isNotEmpty());
	}

	@Test
	void aGuestHasNoPlaceToKeep() throws Exception {
		mockMvc.perform(get("/users/me/location").header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}
}
