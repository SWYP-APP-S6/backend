package com.swyp.backend.ping.service;

import com.swyp.backend.ping.dto.PingResponse;
import org.springframework.stereotype.Service;

@Service
public class PingService {

	public PingResponse ping() {
		return new PingResponse("pong");
	}
}
