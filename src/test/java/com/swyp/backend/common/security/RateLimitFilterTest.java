package com.swyp.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class RateLimitFilterTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider tokenProvider;

	@Autowired
	RateLimitProperties properties;

	@Test
	void exceedingThePerMinuteLimit_returns429WithRetryAfter() throws Exception {
		long principal = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		String bearer = "Bearer " + tokenProvider.createAccessToken(TokenRealm.GUEST, principal, "GUEST");
		int limit = properties.limitFor(TokenRealm.GUEST);

		MockHttpServletResponse limited = null;
		int okCount = 0;
		for (int i = 0; i < 2 * limit + 2 && limited == null; i++) {
			MockHttpServletResponse response = mockMvc.perform(
					get("/recipes/categories").header("Authorization", bearer))
				.andReturn().getResponse();
			if (response.getStatus() == 429) {
				limited = response;
			} else {
				assertThat(response.getStatus()).isEqualTo(200);
				okCount++;
			}
		}

		assertThat(limited).isNotNull();
		assertThat(okCount).isGreaterThanOrEqualTo(limit);
		assertThat(limited.getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
		assertThat(Long.parseLong(limited.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
		assertThat((String) JsonPath.read(limited.getContentAsString(), "$.code")).isEqualTo("TOO_MANY_REQUESTS");
	}

	@Test
	void anonymousRequests_areNotRateLimited() throws Exception {
		int limit = properties.limitFor(TokenRealm.GUEST);
		for (int i = 0; i < limit + 1; i++) {
			assertThat(mockMvc.perform(get("/ping")).andReturn().getResponse().getStatus()).isEqualTo(200);
		}
	}
}
