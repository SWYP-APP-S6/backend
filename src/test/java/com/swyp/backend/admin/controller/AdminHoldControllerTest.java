package com.swyp.backend.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreCategory;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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
class AdminHoldControllerTest {

	private static final String EMAIL = "hold-admin@swyp.test";
	private static final String PASSWORD = "hold-admin-1234";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppDataCleaner appDataCleaner;

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

	private User consumer;
	private User anotherConsumer;
	private Product peach;
	private Product onion;
	private Hold live;
	private Hold overdue;
	private Hold completed;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
					new Admin(EMAIL, "Hold Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		anotherConsumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store created = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", null, "0212345678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		created.replaceCategories(Set.of(StoreCategory.FRUIT));
		created.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		created.approve();
		Store store = storeRepository.saveAndFlush(created);
		peach = product(store, "복숭아 4입", 4_000);
		onion = product(store, "양파 1.5kg", 3_000);

		live = hold(consumer, peach, 2, Instant.now().plusSeconds(600));
		overdue = hold(anotherConsumer, onion, 1, Instant.now().minusSeconds(60));
		completed = hold(consumer, onion, 3, Instant.now().plusSeconds(600));
		completed.complete(Instant.now());
		holdRepository.saveAndFlush(completed);
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Product product(Store store, String name, int salePrice) {
		return productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.FRUIT, 10, 10_000, salePrice,
				LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5),
				"https://cdn.example.com/p.jpg"));
	}

	private Hold hold(User user, Product product, int qty, Instant expiresAt) {
		product.hold(qty);
		productRepository.saveAndFlush(product);
		return holdRepository.saveAndFlush(HoldFixture.hold(user, product, qty, expiresAt));
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
	void holds_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/holds")).andExpect(status().isUnauthorized());
	}

	@Test
	void holdsComeNewestFirst_withWhoWhereWhat_andAnOverdueHoldReadsAsExpired() throws Exception {
		mockMvc.perform(get("/admin/holds").header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[0].id").value(completed.getId()))
				.andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$.data.content[0].lineTotal").value(9_000))
				.andExpect(jsonPath("$.data.content[1].id").value(overdue.getId()))
				.andExpect(jsonPath("$.data.content[1].status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.content[1].user.name").value("다른소비자"))
				.andExpect(jsonPath("$.data.content[2].id").value(live.getId()))
				.andExpect(jsonPath("$.data.content[2].status").value("HOLDING"))
				.andExpect(jsonPath("$.data.content[2].store.name").value("청과마을"))
				.andExpect(jsonPath("$.data.content[2].product.name").value("복숭아 4입"));
	}

	@Test
	void holdsNarrowByProduct_byUser_andByStoredStatus() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/holds").param("productId", String.valueOf(onion.getId()))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));

		mockMvc.perform(get("/admin/holds").param("userId", String.valueOf(consumer.getId()))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));

		mockMvc.perform(get("/admin/holds").param("status", "COMPLETED")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(completed.getId()));
	}
}
