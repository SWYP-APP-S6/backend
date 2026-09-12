package com.swyp.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenDeleteRequest(@NotBlank @Size(max = 255) String fcmToken) {
}
