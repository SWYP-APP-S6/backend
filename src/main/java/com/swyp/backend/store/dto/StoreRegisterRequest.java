package com.swyp.backend.store.dto;

import com.swyp.backend.store.entity.StoreCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record StoreRegisterRequest(
		@NotBlank @Size(max = 100) String name,
		@NotEmpty @Size(max = 3, message = "가게 종류는 최대 3개까지 선택할 수 있습니다.")
		Set<StoreCategory> categories,
		@Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 5자리 숫자여야 합니다.") String postalCode,
		@NotBlank @Size(max = 255) String address,
		@Size(max = 255) String addressDetail,
		@NotBlank @Size(max = 20) @Pattern(regexp = "^[0-9-]+$", message = "연락처는 숫자와 하이픈만 사용할 수 있습니다.")
		String phone,
		@NotNull LocalTime businessOpenTime,
		@NotNull LocalTime businessCloseTime,
		@NotEmpty Set<DayOfWeek> businessDays,
		@Size(max = 20) String businessRegistrationNumber,
		String applicationNote) {
}
