package com.swyp.backend.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record StoreRegisterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(max = 255) String address,
		@Size(max = 255) String addressDetail,
		@NotBlank @Pattern(regexp = "^[0-9-]+$", message = "연락처는 숫자와 하이픈만 사용할 수 있습니다.")
		String phone,
		@NotNull LocalTime businessOpenTime,
		@NotNull LocalTime businessCloseTime,
		@Size(max = 20) String businessRegistrationNumber,
		String applicationNote) {
}
