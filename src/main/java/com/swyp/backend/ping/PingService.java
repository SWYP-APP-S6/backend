package com.swyp.backend.ping;

import org.springframework.stereotype.Service;

/** Business layer for the ping feature. Holds no persistence — the walking skeleton stops at the
 *  service layer (a repository joins when the first real entity appears). */
@Service
public class PingService {

	public PingResponse ping() {
		return new PingResponse("pong");
	}
}
