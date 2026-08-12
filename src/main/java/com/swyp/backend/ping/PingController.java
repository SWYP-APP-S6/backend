package com.swyp.backend.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web layer for the ping feature. Delegates to {@link PingService} and returns a DTO — the
 *  controller never touches persistence directly (CLAUDE.md architecture). */
@RestController
public class PingController {

	private final PingService pingService;

	public PingController(PingService pingService) {
		this.pingService = pingService;
	}

	@GetMapping("/ping")
	public PingResponse ping() {
		return pingService.ping();
	}
}
