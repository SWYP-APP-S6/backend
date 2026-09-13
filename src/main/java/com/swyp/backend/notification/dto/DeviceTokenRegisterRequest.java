package com.swyp.backend.notification.dto;

import com.swyp.backend.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceTokenRegisterRequest(
		@NotNull DevicePlatform platform, @NotBlank @Size(max = 255) String fcmToken) {
}
