package com.swyp.backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.common.response.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies {@link GlobalExceptionHandler} maps exceptions to the standard error envelope with the
 * correct status. Uses {@code standaloneSetup} with a throwaway controller — the advice is tested
 * in isolation, before any real endpoint exists. Framework client errors (405, malformed body,
 * type mismatch) must map to 4xx, not the 500 fallback.
 */
class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new ThrowingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void businessException_mapsToErrorEnvelope() throws Exception {
		mockMvc.perform(get("/test/business"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void wrongHttpMethod_mapsToMethodNotAllowed() throws Exception {
		mockMvc.perform(post("/test/business"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.status").value(405))
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void malformedJsonBody_mapsToBadRequest() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void typeMismatch_mapsToBadRequest() throws Exception {
		mockMvc.perform(get("/test/mismatch").param("n", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void unhandledException_mapsToInternalError() throws Exception {
		mockMvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	@RestController
	static class ThrowingController {

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}

		@PostMapping("/test/body")
		void body(@RequestBody Payload payload) {
			// unreachable with malformed JSON — HttpMessageNotReadableException is thrown first
		}

		@GetMapping("/test/mismatch")
		void mismatch(@RequestParam int n) {
			// "abc" fails to bind to int → MethodArgumentTypeMismatchException before this runs
		}

		@GetMapping("/test/boom")
		void boom() {
			throw new RuntimeException("boom");
		}

		record Payload(String name) {
		}
	}
}
