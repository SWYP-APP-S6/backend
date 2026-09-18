package com.swyp.backend.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class AdminUserControllerTest {

	private static final String EMAIL = "user-list-admin@swyp.test";
	private static final String PASSWORD = "user-list-admin-1234";

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
				new Admin(EMAIL, "User List Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		// 다른 테스트가 남긴 가게가 유저를 참조하고 있으면 users 부터 지울 수 없다.
		holdRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		notificationRepository.deleteAll();
		userRepository.deleteAll();
		userRepository.save(new User(UserRole.CONSUMER, "소비자하나", "01011112222", true, Instant.now()));
		userRepository.save(new User(UserRole.CONSUMER, "소비자둘", null, false, Instant.now()));
		userRepository.save(new User(UserRole.OWNER, "판매자하나", "01033334444", false, Instant.now()));
	}

	private String accessToken() throws Exception {
		String body = mockMvc.perform(post("/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.accessToken");
	}

	@Test
	void users_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void users_withoutRoleFilter_returnsEveryone() throws Exception {
		mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void users_filteredByRole_returnsOnlyThatRole() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/users").param("role", "CONSUMER")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.content[*].role").value(org.hamcrest.Matchers.everyItem(
				org.hamcrest.Matchers.equalTo("CONSUMER"))));

		mockMvc.perform(get("/admin/users").param("role", "OWNER")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].nickname").value("판매자하나"));
	}

	@Test
	void phoneNumbers_areReturnedInFull() throws Exception {
		mockMvc.perform(get("/admin/users").param("role", "OWNER")
				.header("Authorization", "Bearer " + accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].phone").value("01033334444"));
	}

	@Test
	void ownerRows_carryTheStoreUnderReview_andConsumerRowsCarryNone() throws Exception {
		User owner = userRepository.findAll().stream()
			.filter(user -> user.getRole() == UserRole.OWNER)
			.findFirst().orElseThrow();
		Store store = storeRepository.save(new Store(
			owner, "판매자하나네", "04524", "서울특별시 강남구 역삼로 1", null, "021234567",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		String token = accessToken();

		mockMvc.perform(get("/admin/users").param("role", "OWNER")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].store.id").value(store.getId()))
			.andExpect(jsonPath("$.data.content[0].store.name").value("판매자하나네"))
			.andExpect(jsonPath("$.data.content[0].store.status").value("PENDING"));

		mockMvc.perform(get("/admin/users").param("role", "CONSUMER")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].store").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void unknownRole_isRejected() throws Exception {
		mockMvc.perform(get("/admin/users").param("role", "NOT_A_ROLE")
				.header("Authorization", "Bearer " + accessToken()))
			.andExpect(status().isBadRequest());
	}
}
