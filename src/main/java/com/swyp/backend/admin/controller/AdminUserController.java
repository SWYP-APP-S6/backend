package com.swyp.backend.admin.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.hold.dto.CancelCreditAdjustRequest;
import com.swyp.backend.hold.dto.CancelCreditBalance;
import com.swyp.backend.hold.service.AdminHoldService;
import com.swyp.backend.user.dto.AdminUserDetailResponse;
import com.swyp.backend.user.dto.UserSummaryResponse;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.service.AdminUserService;
import com.swyp.backend.user.service.UserDeletionService;
import com.swyp.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminUser", description = "관리자 회원")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final UserService userService;
	private final UserDeletionService userDeletionService;
	private final AdminUserService adminUserService;
	private final AdminHoldService adminHoldService;

	@GetMapping
	public ApiResponse<PageResponse<UserSummaryResponse>> getUsers(
			@RequestParam(required = false) UserRole role,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ApiResponse.of(SuccessCode.OK, userService.getUsers(role, pageable));
	}

	@GetMapping("/{id}")
	public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long id) {
		return ApiResponse.of(SuccessCode.OK, adminUserService.getUser(id));
	}

	@PostMapping("/{id}/cancel-credits")
	public ApiResponse<CancelCreditBalance> adjustCancelCredits(
			@PathVariable Long id, @Valid @RequestBody CancelCreditAdjustRequest request) {
		return ApiResponse.of(
				SuccessCode.OK, adminHoldService.adjustCancelCredits(id, request.delta()));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> deleteUser(@PathVariable Long id) {
		userDeletionService.delete(id);
		return ApiResponse.of(SuccessCode.OK);
	}
}
