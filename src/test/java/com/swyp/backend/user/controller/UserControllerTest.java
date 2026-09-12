package com.swyp.backend.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class UserControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private User save(UserRole role, String nickname, String phone, boolean marketingOptIn) {
		return userRepository.saveAndFlush(
				new User(role, nickname, phone, marketingOptIn, Instant.parse("2026-09-01T00:00:00Z")));
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
				TokenRealm.USER, user.getId(), user.getRole().name());
	}

	@Test
	void itAnswersWithTheProfileAndTheTermsStateOfTheCallingConsumer() throws Exception {
		User consumer = save(UserRole.CONSUMER, "망원동 주민", "010-1234-5678", true);

		mockMvc.perform(get("/users/me").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(consumer.getId()))
			.andExpect(jsonPath("$.data.role").value("CONSUMER"))
			.andExpect(jsonPath("$.data.nickname").value("망원동 주민"))
			.andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.marketingOptIn").value(true))
			.andExpect(jsonPath("$.data.termsAgreedAt").value("2026-09-01T00:00:00Z"))
			.andExpect(jsonPath("$.data.joinedAt").exists());
	}

	@Test
	void aMissingPhoneArrivesAsNullRatherThanBreakingTheResponse() throws Exception {
		User consumer = save(UserRole.CONSUMER, "전화번호 없음", null, false);

		mockMvc.perform(get("/users/me").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.phone").doesNotExist())
			.andExpect(jsonPath("$.data.marketingOptIn").value(false));
	}

	@Test
	void theOwnerAppReadsItsOwnProfileThroughTheSameEndpoint() throws Exception {
		User owner = save(UserRole.OWNER, "청과마을 사장", null, false);

		mockMvc.perform(get("/users/me").header("Authorization", bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("OWNER"));
	}

	@Test
	void aGuestHasNoProfileToRead() throws Exception {
		mockMvc.perform(get("/users/me").header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	@Test
	void aTokenOfAUserThatNoLongerExistsIsNotFoundRatherThanAServerError() throws Exception {
		mockMvc.perform(get("/users/me").header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.USER, 9_999_999L, "CONSUMER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void anAdminTokenCannotReadAnAppProfile() throws Exception {
		mockMvc.perform(get("/users/me").header("Authorization",
				"Bearer " + tokenProvider.createAccessToken(TokenRealm.ADMIN, 1L, "SUPER")))
			.andExpect(status().isForbidden());
	}
}
