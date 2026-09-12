package com.swyp.backend.user.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.user.dto.MeResponse;
import com.swyp.backend.user.dto.UserSummaryResponse;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.function.UserFunction;
import com.swyp.backend.user.function.UserLocationFunction;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

	private final UserFunction userFunction;
	private final UserLocationFunction userLocationFunction;

	public MeResponse getMe(Long userId) {
		return MeResponse.from(userFunction.getById(userId));
	}

	public PageResponse<UserSummaryResponse> getUsers(UserRole role, Pageable pageable) {
		Page<User> users = userFunction.findAllByRole(role, pageable);

		Map<Long, String> regions = userLocationFunction.regionNamesByUserId(users.getContent().stream().map(User::getId).toList());
		List<UserSummaryResponse> content = users.getContent().stream()
				.map(user -> UserSummaryResponse.of(user, regions.get(user.getId())))
				.toList();

		return PageResponse.of(content, users);
	}

}
