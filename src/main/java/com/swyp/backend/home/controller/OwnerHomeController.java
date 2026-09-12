package com.swyp.backend.home.controller;

import com.swyp.backend.common.response.ApiResponse;
import com.swyp.backend.common.response.SuccessCode;
import com.swyp.backend.home.dto.OwnerHomeResponse;
import com.swyp.backend.home.service.OwnerHomeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OwnerHome", description = "점주 홈")
@RestController
@RequestMapping("/owner/home")
@RequiredArgsConstructor
public class OwnerHomeController {

	private final OwnerHomeService ownerHomeService;

	@GetMapping
	public ApiResponse<OwnerHomeResponse> getOwnerHome(@AuthenticationPrincipal Long ownerId) {
		return ApiResponse.of(SuccessCode.OK, ownerHomeService.getOwnerHome(ownerId));
	}
}
