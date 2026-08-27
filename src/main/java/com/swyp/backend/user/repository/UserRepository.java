package com.swyp.backend.user.repository;

import com.swyp.backend.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByOauthProviderAndOauthProviderId(
			String oauthProvider, String oauthProviderId);
}
