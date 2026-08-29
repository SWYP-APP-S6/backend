package com.swyp.backend.admin.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.user.dto.UserSummaryResponse;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final UserService userService;

	@GetMapping
	public ApiResponse<PageResponse<UserSummaryResponse>> getUsers(
			@RequestParam(required = false) UserRole role,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, userService.getUsers(role, pageable));
	}
}
