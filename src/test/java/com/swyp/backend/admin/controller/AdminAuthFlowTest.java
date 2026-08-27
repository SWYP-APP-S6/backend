package com.swyp.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class AdminAuthFlowTest {

	private static final String EMAIL = "auth-flow-admin@swyp.test";
	private static final String PASSWORD = "swyp-admin-1234";
	private static final String LOGIN_BODY = """
		{"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD);

	private static final String PW_EMAIL = "password-change-admin@swyp.test";
	private static final String PW_OLD = "initial-password-1234";
	private static final String PW_NEW = "changed-password-5678";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void createAdmin() {
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
				new Admin(EMAIL, "Auth Flow Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		// 테스트 밖에는 트랜잭션이 없어 조회한 엔티티가 detached 다 — 더티 체킹이 돌지 않으므로
		// 명시적으로 저장해야 앞선 테스트가 바꿔 놓은 비밀번호가 되돌아간다.
		adminRepository.findByEmail(PW_EMAIL).ifPresentOrElse(
			admin -> {
				admin.changePassword(passwordEncoder.encode(PW_OLD));
				adminRepository.save(admin);
			},
			() -> adminRepository.save(
				new Admin(PW_EMAIL, "Password Admin", AdminType.MANAGER, passwordEncoder.encode(PW_OLD))));
	}

	private String accessTokenFor(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	private static String passwordChangeBody(String current, String next) {
		return """
			{"currentPassword":"%s","newPassword":"%s"}""".formatted(current, next);
	}

	@Test
	void changePassword_withCorrectCurrentPassword_lettsTheAdminLogInWithTheNewOne() throws Exception {
		String accessToken = accessTokenFor(PW_EMAIL, PW_OLD);

		mockMvc.perform(put("/admin/auth/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordChangeBody(PW_OLD, PW_NEW)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}""".formatted(PW_EMAIL, PW_NEW)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}""".formatted(PW_EMAIL, PW_OLD)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void changePassword_withWrongCurrentPassword_isRejected() throws Exception {
		String accessToken = accessTokenFor(PW_EMAIL, PW_OLD);

		mockMvc.perform(put("/admin/auth/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordChangeBody("not-the-current-password", PW_NEW)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void changePassword_withoutToken_isRejected() throws Exception {
		mockMvc.perform(put("/admin/auth/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordChangeBody(PW_OLD, PW_NEW)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void changePassword_withTooShortNewPassword_isRejected() throws Exception {
		String accessToken = accessTokenFor(PW_EMAIL, PW_OLD);

		mockMvc.perform(put("/admin/auth/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(passwordChangeBody(PW_OLD, "short")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void login_withExistingSuperAdmin_returnsTokens() throws Exception {
		mockMvc.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andExpect(jsonPath("$.data.refreshToken").exists());
	}

	@Test
	void login_withWrongPassword_returnsUnauthorized() throws Exception {
		String body = """
			{"email":"%s","password":"wrong"}""".formatted(EMAIL);
		mockMvc.perform(post("/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void refresh_rotatesToken_andRejectsTheConsumedOne() throws Exception {
		String oldRefresh = login("$.data.refreshToken");

		String rotated = mockMvc.perform(refreshWith(oldRefresh))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(rotated, "$.data.refreshToken")).isNotEqualTo(oldRefresh);

		mockMvc.perform(refreshWith(oldRefresh)).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_revokesRefreshToken() throws Exception {
		String refresh = login("$.data.refreshToken");

		mockMvc.perform(post("/admin/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk());

		mockMvc.perform(refreshWith(refresh)).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedEndpoint_withoutToken_returnsUnauthorizedEnvelope() throws Exception {
		mockMvc.perform(get("/admin/members"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void protectedEndpoint_withValidToken_passesSecurity() throws Exception {
		String access = login("$.data.accessToken");

		mockMvc.perform(get("/admin/members").header("Authorization", "Bearer " + access))
			.andExpect(status().isNotFound());
	}

	private String login(String jsonPath) throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, jsonPath);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshWith(String token) {
		return post("/admin/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refreshToken\":\"" + token + "\"}");
	}
}
