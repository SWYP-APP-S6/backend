package com.swyp.backend.ping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test for the ping feature — the reference pattern for controller tests. Fast
 * ({@code @WebMvcTest} loads only the web layer, no DB), the real {@link PingService} is imported so
 * the controller-to-service call is exercised for real. Security filters are disabled
 * ({@code addFilters = false}); a real SecurityFilterChain arrives with the auth work.
 */
@WebMvcTest(PingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PingService.class)
class PingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void ping_returnsPong() throws Exception {
		mockMvc.perform(get("/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("OK"))
			.andExpect(jsonPath("$.data.message").value("pong"));
	}
}
