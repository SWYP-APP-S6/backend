package com.swyp.backend.hold.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.hold.dto.AdminHoldQuery;
import com.swyp.backend.hold.dto.AdminHoldResponse;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.function.HoldFunction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHoldService {

	private final HoldFunction holdFunction;
	private final Clock clock;

	public PageResponse<AdminHoldResponse> getHolds(AdminHoldQuery query, Pageable pageable) {
		Instant now = Instant.now(clock);
		Page<Hold> holds = holdFunction.findForAdmin(query, pageable);
		List<AdminHoldResponse> content = holds.getContent().stream()
				.map(hold -> AdminHoldResponse.from(hold, now))
				.toList();
		return PageResponse.of(content, holds);
	}
}
