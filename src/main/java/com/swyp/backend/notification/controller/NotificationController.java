package com.swyp.backend.notification.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.notification.dto.NotificationResponse;
import com.swyp.backend.notification.dto.NotificationsReadResponse;
import com.swyp.backend.notification.dto.NotificationsResponse;
import com.swyp.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public ApiResponse<NotificationsResponse> getNotifications(
			@AuthenticationPrincipal Long userId, @PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, notificationService.getNotifications(userId, pageable));
	}

	@PatchMapping("/{notificationId}/read")
	public ApiResponse<NotificationResponse> readNotification(
			@AuthenticationPrincipal Long userId, @PathVariable Long notificationId) {
		return ApiResponse.of(SuccessCode.OK, notificationService.read(userId, notificationId));
	}

	@PostMapping("/read-all")
	public ApiResponse<NotificationsReadResponse> readAllNotifications(
			@AuthenticationPrincipal Long userId) {
		return ApiResponse.of(SuccessCode.OK, notificationService.readAll(userId));
	}
}
