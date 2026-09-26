package com.swyp.backend.admin.controller;

import com.swyp.backend.analytics.dto.DomainEventFilter;
import com.swyp.backend.analytics.dto.DomainEventResponse;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.service.DomainEventService;
import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminEvent", description = "관리자 행동 로그")
@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class AdminDomainEventController {

	private final DomainEventService domainEventService;

	@GetMapping
	@PageQueryParams
	public ApiResponse<PageResponse<DomainEventResponse>> getDomainEvents(
			@RequestParam(required = false) DomainEventType type,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) Long storeId,
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) Long holdId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		DomainEventFilter filter = new DomainEventFilter(
				type, userId, storeId, productId, holdId, from, to);
		return ApiResponse.of(SuccessCode.OK, domainEventService.getEvents(filter, pageable));
	}
}
