package com.swyp.backend.ping.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.ping.dto.PingResponse;
import com.swyp.backend.ping.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web layer for the ping feature. Delegates to {@link PingService} and wraps the DTO in the
 *  standard {@link ApiResponse} envelope — the controller never touches persistence directly. */
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
