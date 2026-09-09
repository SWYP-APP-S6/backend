package com.swyp.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class GuestAuthFlowTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	StringRedisTemplate redis;

	@Value("${auth.guest-issue-limit-per-day}")
	int issueLimitPerDay;

	@Value("${jwt.guest-access-ttl}")
	Duration guestTtl;

	private ResultActions issue(String installId) throws Exception {
		return mockMvc.perform(post("/auth/guest")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"installId\":\"" + installId + "\"}"));
	}

	@Test
	void issue_returnsAGuestAccessToken_andRemembersTheInstall() throws Exception {
		String installId = UUID.randomUUID().toString();

		String body = issue(installId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isString())
			.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.data.accessToken");

		JwtTokenProvider.AccessToken parsed = tokenProvider.parseAccessToken(accessToken);
		assertThat(parsed.realm()).isEqualTo(TokenRealm.GUEST);
		assertThat(parsed.role()).isEqualTo("GUEST");
		assertThat(redis.opsForValue().get("guest:" + parsed.principalId())).isEqualTo(installId);
		assertThat(redis.getExpire("guest:" + parsed.principalId())).isPositive();
		Instant expiresAt = tokenProvider.parse("access", accessToken).getExpiration().toInstant();
		assertThat(expiresAt).isAfter(Instant.now().plus(guestTtl).minus(Duration.ofMinutes(1)));
	}

	@Test
	void issue_withoutAnInstallId_isRejected() throws Exception {
		mockMvc.perform(post("/auth/guest")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"installId\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void issue_beyondTheDailyLimitForOneInstall_isRejected() throws Exception {
		String installId = UUID.randomUUID().toString();
		for (int i = 0; i < issueLimitPerDay; i++) {
			issue(installId).andExpect(status().isOk());
		}

		issue(installId)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("GUEST_ISSUE_LIMIT_EXCEEDED"));

		issue(UUID.randomUUID().toString()).andExpect(status().isOk());
	}

	@Test
	void guestToken_canBrowse_butCannotUseMemberOnlyEndpoints() throws Exception {
		String body = issue(UUID.randomUUID().toString())
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String bearer = "Bearer " + JsonPath.read(body, "$.data.accessToken");

		mockMvc.perform(get("/recipes/categories").header("Authorization", bearer))
			.andExpect(status().isOk());

		mockMvc.perform(get("/owner/stores/me").header("Authorization", bearer))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}
}
