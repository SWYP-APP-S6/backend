package com.swyp.backend.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.common.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AdminRepositoryTest {

	@Autowired
	AdminRepository adminRepository;

	@Test
	void save_populatesAuditTimestamps() {
		Admin saved = adminRepository.save(new Admin("alice@swyp.com", "Alice", AdminType.SUPER, "hash"));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void softDelete_hidesRowFromQueries() {
		Admin admin = adminRepository.save(new Admin("sam@swyp.com", "Sam", AdminType.MANAGER, "hash"));

		adminRepository.delete(admin);
		adminRepository.flush();

		assertThat(adminRepository.findById(admin.getId())).isEmpty();
	}

	@Test
	void email_isReusableAfterSoftDelete() {
		Admin first = adminRepository.save(new Admin("dup@swyp.com", "First", AdminType.DEVELOPER, "hash"));
		adminRepository.delete(first);
		adminRepository.flush();

		Admin second = adminRepository.save(new Admin("dup@swyp.com", "Second", AdminType.DEVELOPER, "hash"));
		adminRepository.flush();

		assertThat(second.getId()).isNotNull();
	}
}
