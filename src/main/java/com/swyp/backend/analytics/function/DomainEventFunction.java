package com.swyp.backend.analytics.function;

import com.swyp.backend.analytics.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventFunction {

	private final DomainEventRepository domainEventRepository;

	public void deleteAllInvolving(Long userId) {
		domainEventRepository.deleteAllInvolving(userId);
	}
}
