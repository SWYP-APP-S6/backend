package com.swyp.backend.analytics.service;

import com.swyp.backend.analytics.dto.DomainEventFilter;
import com.swyp.backend.analytics.dto.DomainEventResponse;
import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.function.DomainEventFunction;
import com.swyp.backend.common.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DomainEventService {

	private final DomainEventFunction domainEventFunction;

	public PageResponse<DomainEventResponse> getEvents(DomainEventFilter filter, Pageable pageable) {
		Page<DomainEvent> events = domainEventFunction.findEvents(filter, pageable);
		List<DomainEventResponse> content = events.getContent().stream()
				.map(DomainEventResponse::from)
				.toList();
		return PageResponse.of(content, events);
	}
}
