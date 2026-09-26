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
import org.hamcrest.Matchers;
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
class AdminProductControllerTest {

	private static final String EMAIL = "product-admin@swyp.test";
	private static final String PASSWORD = "product-admin-1234";

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
	PasswordEncoder passwordEncoder;

	private Store approvedStore;
	private Store pendingStore;
	private Product visible;
	private Product soldOut;
	private Product ended;
	private Product inPendingStore;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
					new Admin(EMAIL, "Product Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		approvedStore = store("청과마을", true);
		pendingStore = store("심사중가게", false);

		LocalDateTime now = LocalDateTime.now();
		visible = product(approvedStore, "복숭아 4입", 3, now.plusHours(5));
		soldOut = product(approvedStore, "딸기 1팩", 1, now.plusHours(5));
		soldOut.hold(1);
		productRepository.saveAndFlush(soldOut);
		ended = product(approvedStore, "어제 상추", 3, now.minusHours(1));
		inPendingStore = product(pendingStore, "감자 1kg", 3, now.plusHours(5));
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Store store(String name, boolean approved) {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, name + " 점주", null, false, Instant.now()));
		Store store = new Store(
				owner, name, "04524", "서울 마포구 망원로 12", null, "0212345678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(0, 0), LocalTime.of(23, 59));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		if (approved) {
			store.approve();
		}
		return storeRepository.saveAndFlush(store);
	}

	private Product product(Store store, String name, int qty, LocalDateTime pickupEndAt) {
		return productRepository.saveAndFlush(new Product(
				store, name, ProductCategory.FRUIT, qty, 10_000, 4_000,
				pickupEndAt.minusHours(6), pickupEndAt, "https://cdn.example.com/p.jpg"));
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
	void products_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/products")).andExpect(status().isUnauthorized());
	}

	@Test
	void everyProductComesWithItsVisibilityDiagnosis() throws Exception {
		mockMvc.perform(get("/admin/products").header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].visibleToConsumers".formatted(visible.getId()))
						.value(true))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].hiddenReasons[*]".formatted(visible.getId()))
						.isEmpty())
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].hiddenReasons[*]".formatted(soldOut.getId()))
						.value(Matchers.contains("NO_STOCK")))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].status".formatted(soldOut.getId()))
						.value("SOLD_OUT"))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].hiddenReasons[*]".formatted(ended.getId()))
						.value(Matchers.contains("PICKUP_ENDED")))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].status".formatted(ended.getId()))
						.value("CLOSED"))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].hiddenReasons[*]".formatted(inPendingStore.getId()))
						.value(Matchers.contains("STORE_NOT_APPROVED")))
				.andExpect(jsonPath("$.data.content[?(@.id == %d)].store.name".formatted(inPendingStore.getId()))
						.value("심사중가게"));
	}

	@Test
	void theHiddenTabListsOnlyWhatConsumersCannotSee() throws Exception {
		mockMvc.perform(get("/admin/products").param("filter", "HIDDEN")
						.header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[*].id").value(
						Matchers.not(Matchers.hasItem(visible.getId().intValue()))));
	}

	@Test
	void theListNarrowsByStoreAndByTab() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/products").param("storeId", String.valueOf(pendingStore.getId()))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("감자 1kg"));

		mockMvc.perform(get("/admin/products").param("filter", "SOLD_OUT")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("딸기 1팩"));

		mockMvc.perform(get("/admin/products").param("filter", "CLOSED")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].name").value("어제 상추"));

		mockMvc.perform(get("/admin/products").param("filter", "ON_SALE")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));
	}
}
