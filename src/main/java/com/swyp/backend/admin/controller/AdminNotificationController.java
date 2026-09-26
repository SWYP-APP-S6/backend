package com.swyp.backend.admin.controller;

import com.swyp.backend.common.openapi.PageQueryParams;
import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.notification.dto.AdminNotificationQuery;
import com.swyp.backend.notification.dto.AdminNotificationResponse;
import com.swyp.backend.notification.dto.PushOutboxSummaryResponse;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.service.AdminNotificationService;
import com.swyp.backend.notification.service.NotificationPushService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminNotification", description = "관리자 알림·푸시 아웃박스")
@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

	private final AdminNotificationService adminNotificationService;
	private final NotificationPushService notificationPushService;

	@GetMapping
	@PageQueryParams
	public ApiResponse<PageResponse<AdminNotificationResponse>> getAdminNotifications(
			@RequestParam(required = false) NotificationPushState pushState,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) NotificationType type,
			@Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
		AdminNotificationQuery query = new AdminNotificationQuery(pushState, userId, type);
		return ApiResponse.of(
				SuccessCode.OK, adminNotificationService.getNotifications(query, pageable));
	}

	@GetMapping("/push-summary")
	public ApiResponse<PushOutboxSummaryResponse> getPushSummary() {
		return ApiResponse.of(SuccessCode.OK, adminNotificationService.getPushSummary());
	}

	@PostMapping("/{id}/push-retry")
	public ApiResponse<AdminNotificationResponse> retryPush(@PathVariable Long id) {
		notificationPushService.resend(id);
		return ApiResponse.of(SuccessCode.OK, adminNotificationService.getNotification(id));
	}
}
