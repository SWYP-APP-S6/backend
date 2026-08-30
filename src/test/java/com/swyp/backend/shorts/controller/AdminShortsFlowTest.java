package com.swyp.backend.shorts.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.shorts.exception.ShortsErrorCode;
import com.swyp.backend.shorts.service.ShortsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class AdminShortsFlowTest {

	private static final String EMAIL = "shorts-admin@swyp.test";
	private static final String PASSWORD = "shorts-admin-1234";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	// 파이프라인은 별도 프로세스라 테스트에서 띄우지 않는다. 검증 대상은 이 백엔드의
	// 라우팅·인가·envelope 이지 파이프라인 동작이 아니다.
	@MockitoBean
	ShortsClient shortsClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
				new Admin(EMAIL, "Shorts Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
	}

	private String accessToken() throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	@Test
	void rejectsAnonymousAccess() throws Exception {
		mockMvc.perform(get("/admin/shorts/sources"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void servesPipelineDataToAnAuthenticatedAdmin() throws Exception {
		given(shortsClient.get(anyString()))
			.willReturn(objectMapper.readTree("[{\"id\":1,\"title\":\"지혜의 향연\"}]"));

		mockMvc.perform(get("/admin/shorts/sources")
				.header("Authorization", "Bearer " + accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].title").value("지혜의 향연"));
	}

	@Test
	void surfacesPipelineOutageAsServiceUnavailable() throws Exception {
		given(shortsClient.get(anyString()))
			.willThrow(new BusinessException(ShortsErrorCode.SHORTS_UNAVAILABLE));

		mockMvc.perform(get("/admin/shorts/health")
				.header("Authorization", "Bearer " + accessToken()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SHORTS_UNAVAILABLE"));
	}
}
