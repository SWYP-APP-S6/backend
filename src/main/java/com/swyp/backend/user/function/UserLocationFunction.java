package com.swyp.backend.user.function;

import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.repository.UserLocationRepository;
import com.swyp.backend.user.entity.User;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLocationFunction {

	private final UserLocationRepository userLocationRepository;

	public Optional<UserLocation> findOf(Long userId) {
		return userLocationRepository.findByUserId(userId);
	}

	// 유저당 한 행이라(user_id 가 기본키) 새로 만들거나 그 자리를 덮는다.
	public UserLocation setRegion(
			User user, String regionName, BigDecimal latitude, BigDecimal longitude) {
		UserLocation location = userLocationRepository.findByUserId(user.getId())
				.orElseGet(() -> new UserLocation(user, null, regionName, latitude, longitude));
		location.changeRegion(location.getRegionCode(), regionName, latitude, longitude);
		return userLocationRepository.save(location);
	}

	public Map<Long, String> regionNamesByUserId(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userLocationRepository.findByUserIdIn(userIds).stream()
				.collect(Collectors.toMap(UserLocation::getUserId, UserLocation::getRegionName));
	}
}
