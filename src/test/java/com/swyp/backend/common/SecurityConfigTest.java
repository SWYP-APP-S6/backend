package com.swyp.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class SecurityConfigTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtTokenProvider tokenProvider;

	private String accessTokenFor(TokenRealm realm, String role) {
		return tokenProvider.createAccessToken(realm, 1L, role);
	}

	private String bearer(TokenRealm realm, String role) {
		return "Bearer " + accessTokenFor(realm, role);
	}

	@Test
	void ping_isReachableThroughSecurityFilterChain() throws Exception {
		mockMvc.perform(get("/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.message").value("pong"));
	}

	@Test
	void openApiDocs_isPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void recipes_withoutAToken_isUnauthorized() throws Exception {
		mockMvc.perform(get("/recipes/categories"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void recipes_areBrowsableByGuestsConsumersAndAdmins() throws Exception {
		mockMvc.perform(get("/recipes/categories").header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/recipes/categories").header("Authorization", bearer(TokenRealm.USER, "CONSUMER")))
			.andExpect(status().isOk());
		mockMvc.perform(get("/recipes/categories").header("Authorization", bearer(TokenRealm.ADMIN, "SUPER")))
			.andExpect(status().isOk());
	}

	@Test
	void browseEndpoints_rejectOwnerTokens() throws Exception {
		mockMvc.perform(get("/recipes/categories").header("Authorization", bearer(TokenRealm.USER, "OWNER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void browseEndpoints_openOnlyGetToGuests() throws Exception {
		mockMvc.perform(post("/recipes/1").header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	@Test
	void guestToken_onAMemberOnlyEndpoint_getsLoginRequired() throws Exception {
		mockMvc.perform(get("/owner/stores/me").header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		mockMvc.perform(get("/admin/users").header("Authorization", bearer(TokenRealm.GUEST, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	@Test
	void adminEndpoints_rejectUserRealmTokens() throws Exception {
		mockMvc.perform(get("/admin/users").header("Authorization", bearer(TokenRealm.USER, "OWNER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void corsPreflight_onAuthenticatedPath_answersWithoutCredentials() throws Exception {
		mockMvc.perform(options("/admin/anything")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void ownerPath_withConsumerToken_isForbidden() throws Exception {
		mockMvc.perform(get("/owner/stores/me")
				.header("Authorization", "Bearer " + accessTokenFor(TokenRealm.USER, "CONSUMER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerPath_withAdminToken_isForbidden() throws Exception {
		mockMvc.perform(get("/owner/stores/me")
				.header("Authorization", "Bearer " + accessTokenFor(TokenRealm.ADMIN, "SUPER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerPath_withOwnerToken_passesAuthorization() throws Exception {
		mockMvc.perform(get("/owner/anything")
				.header("Authorization", "Bearer " + accessTokenFor(TokenRealm.USER, "OWNER")))
			.andExpect(status().isNotFound());
	}

	@Test
	void corsPreflight_fromUnconfiguredOrigin_isRejected() throws Exception {
		mockMvc.perform(options("/recipes")
				.header("Origin", "https://not-allowed.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}
}
