package com.swyp.backend.user.function;

import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.repository.UserLocationRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLocationFunction {

	private final UserLocationRepository userLocationRepository;

	public List<UserLocation> findByUserIdIn(Collection<Long> userIds) {
		return userLocationRepository.findByUserIdIn(userIds);
	}
}
