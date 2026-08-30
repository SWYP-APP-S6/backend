package com.swyp.backend.shorts.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.shorts.dto.ClipReviewRequest;
import com.swyp.backend.shorts.dto.RankRequest;
import com.swyp.backend.shorts.dto.SourceCreateRequest;
import com.swyp.backend.shorts.exception.ShortsErrorCode;
import com.swyp.backend.shorts.service.ShortsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminShortsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminShortsControllerTest {

	@Autowired
	MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	ShortsService shortsService;

	private JsonNode node(String json) {
		return objectMapper.readTree(json);
	}

	@Test
	void wrapsPipelineResponseInTheStandardEnvelope() throws Exception {
		given(shortsService.listSources()).willReturn(node("[{\"id\":1,\"title\":\"강연\"}]"));

		mockMvc.perform(get("/admin/shorts/sources"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.data[0].title").value("강연"));
	}

	@Test
	void translatesUnreachablePipelineIntoServiceUnavailable() throws Exception {
		given(shortsService.health()).willThrow(new BusinessException(ShortsErrorCode.SHORTS_UNAVAILABLE));

		mockMvc.perform(get("/admin/shorts/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SHORTS_UNAVAILABLE"));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void passesTheAuthenticatedAdminIdToThePipeline() throws Exception {
		given(shortsService.runRank(anyLong(), any(), any())).willReturn(node("{\"id\":\"job1\"}"));
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(42L, null, List.of()));

		mockMvc.perform(post("/admin/shorts/sources/7/rank")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"criteria\":\"핵심 논지\"}"))
			.andExpect(status().isOk());

		verify(shortsService).runRank(eq(7L), any(RankRequest.class), eq(42L));
	}

	@Test
	void rejectsSourceWithoutRequiredFields() throws Exception {
		mockMvc.perform(post("/admin/shorts/sources")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void rejectsVerdictOutsideTheAllowedSet() throws Exception {
		mockMvc.perform(post("/admin/shorts/clips/1/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\":\"MAYBE\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void acceptsValidReview() throws Exception {
		given(shortsService.reviewClip(anyLong(), any(ClipReviewRequest.class), any()))
			.willReturn(node("{\"ok\":true}"));

		mockMvc.perform(post("/admin/shorts/clips/1/review")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\":\"OK\",\"note\":\"쓸만함\"}"))
			.andExpect(status().isCreated());
	}

	@Test
	void createsSourceWithDefaults() throws Exception {
		given(shortsService.createSource(any(SourceCreateRequest.class), any()))
			.willReturn(node("{\"sourceId\":3}"));

		mockMvc.perform(post("/admin/shorts/sources")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"path\":\"a.mp4\",\"title\":\"강연\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.sourceId").value(3));
	}
}
