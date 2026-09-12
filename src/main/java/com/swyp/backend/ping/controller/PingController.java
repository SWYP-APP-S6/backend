package com.swyp.backend.ping.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.ping.dto.PingResponse;
import com.swyp.backend.ping.service.PingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "헬스체크")
@RestController
public class PingController {

	private final PingService pingService;

	public PingController(PingService pingService) {
		this.pingService = pingService;
	}

	@GetMapping("/ping")
	public ApiResponse<PingResponse> ping() {
		return ApiResponse.of(SuccessCode.OK, pingService.ping());
	}
}
