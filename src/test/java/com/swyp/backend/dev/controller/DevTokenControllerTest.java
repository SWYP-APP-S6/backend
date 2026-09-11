package com.swyp.backend.dev.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class DevTokenControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void issuesAnAppUserTokenWithoutAuthentication() throws Exception {
		MvcResult result = mockMvc
				.perform(post("/dev/test-token").param("role", "CONSUMER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("CONSUMER"))
				.andReturn();

		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
		JwtTokenProvider.AccessToken parsed =
				tokenProvider.parseAccessToken(data.get("accessToken").asString());

		assertThat(parsed.realm()).isEqualTo(TokenRealm.USER);
		assertThat(parsed.role()).isEqualTo(UserRole.CONSUMER.name());
		assertThat(parsed.principalId()).isEqualTo(data.get("userId").asLong());
		assertThat(userRepository.findById(parsed.principalId()))
				.get()
				.extracting(user -> user.getRole())
				.isEqualTo(UserRole.CONSUMER);
	}

	@Test
	void reusesTheSameTestUserAcrossCalls() throws Exception {
		long first = issueUserId();
		long second = issueUserId();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void rejectsAnUnknownRole() throws Exception {
		mockMvc
				.perform(post("/dev/test-token").param("role", "ADMIN"))
				.andExpect(status().isBadRequest());
	}

	private long issueUserId() throws Exception {
		MvcResult result = mockMvc
				.perform(post("/dev/test-token").param("role", "OWNER"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper
				.readTree(result.getResponse().getContentAsString())
				.get("data")
				.get("userId")
				.asLong();
	}
}
