package com.swyp.backend.user.function;

import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.repository.UserLocationRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLocationFunction {

	private final UserLocationRepository userLocationRepository;

	public Map<Long, String> regionNamesByUserId(Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userLocationRepository.findByUserIdIn(userIds).stream()
				.collect(Collectors.toMap(UserLocation::getUserId, UserLocation::getRegionName));
	}
}
