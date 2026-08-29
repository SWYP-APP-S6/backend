package com.swyp.backend.user.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.user.dto.UserSummaryResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserLocationRepository;
import com.swyp.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;
	private final UserLocationRepository userLocationRepository;

	public PageResponse<UserSummaryResponse> getUsers(UserRole role, Pageable pageable) {
		Page<User> users = role == null
				? userRepository.findAll(pageable)
				: userRepository.findByRole(role, pageable);

		Map<Long, String> regions = regionNamesFor(users.getContent());
		List<UserSummaryResponse> content = users.getContent().stream()
				.map(user -> UserSummaryResponse.of(user, regions.get(user.getId())))
				.toList();

		return PageResponse.of(content, users);
	}

	private Map<Long, String> regionNamesFor(List<User> users) {
		if (users.isEmpty()) {
			return Map.of();
		}
		List<Long> userIds = users.stream().map(User::getId).toList();
		return userLocationRepository.findByUserIdIn(userIds).stream()
				.collect(Collectors.toMap(UserLocation::getUserId, UserLocation::getRegionName));
	}
}
