package com.swyp.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full app with the real security filter chain (no {@code addFilters = false}) to prove the
 * interim {@link SecurityConfig} leaves endpoints reachable — without it, Spring Security's default
 * lockdown would 401 every request, including a plain {@code GET /ping}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class SecurityConfigTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void ping_isReachableThroughSecurityFilterChain() throws Exception {
		mockMvc.perform(get("/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.message").value("pong"));
	}
}
