package com.swyp.backend.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerProductControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private User owner;
	private Store store;
	private String token;

	@BeforeEach
	void setUp() {
		owner = userRepository.saveAndFlush(new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		store = storeRepository.saveAndFlush(new Store(
			owner, "테스트가게", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), owner.getRole().name());
	}

	private static String registerBody(String name, int originalPrice, int salePrice) {
		return """
			{"name":"%s","category":"VEGETABLE","initialQty":10,"originalPrice":%d,"salePrice":%d,\
			"photoUrl":"https://example.com/a.jpg","ingredientTags":[]}""".formatted(name, originalPrice, salePrice);
	}

	private Product createProduct(String name, int initialQty) {
		Product product = new Product(
			store, name, ProductCategory.VEGETABLE, initialQty, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg");
		return productRepository.saveAndFlush(product);
	}

	private User createConsumer() {
		return userRepository.saveAndFlush(new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
	}

	@Test
	void registerProduct_succeeds() throws Exception {
		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("당근", 1000, 800)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("당근"))
			.andExpect(jsonPath("$.data.status").value("ON_SALE"))
			.andExpect(jsonPath("$.data.discountRate").value(20))
			.andExpect(jsonPath("$.data.availableQty").value(10));
	}

	@Test
	void registerProduct_rejectsASalePriceNotLowerThanOriginal() throws Exception {
		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("당근", 1000, 1000)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PRICE"));
	}

	@Test
	void registerProduct_withoutAStore_isRejected() throws Exception {
		User ownerWithoutStore = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "가게없는점주", null, false, Instant.now()));
		String tokenWithoutStore =
			tokenProvider.createAccessToken(TokenRealm.USER, ownerWithoutStore.getId(), UserRole.OWNER.name());

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + tokenWithoutStore)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("당근", 1000, 800)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_REGISTERED"));
	}

	@Test
	void getMyProducts_listsOnlyMyStoresProducts() throws Exception {
		createProduct("당근", 10);
		createProduct("감자", 5);

		mockMvc.perform(get("/owner/products").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void getMyProducts_flagsOversoldProducts() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15))));
		product.adjustAvailableQty(1);
		productRepository.saveAndFlush(product);

		mockMvc.perform(get("/owner/products").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].oversold").value(true));
	}

	@Test
	void getMyProduct_includesCompletedQty() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		Hold completedHold = new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15)));
		completedHold.complete(Instant.now());
		holdRepository.saveAndFlush(completedHold);

		mockMvc.perform(get("/owner/products/" + product.getId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.completedQty").value(3));
	}

	@Test
	void getMyProduct_forAnotherStoresProduct_isNotFound() throws Exception {
		User otherOwner = userRepository.saveAndFlush(new User(UserRole.OWNER, "다른점주", null, false, Instant.now()));
		Store otherStore = storeRepository.saveAndFlush(new Store(
			otherOwner, "다른가게", "주소", null, "0210001000",
			new BigDecimal("37.1"), new BigDecimal("127.1"), LocalTime.of(9, 0), LocalTime.of(21, 0)));
		Product othersProduct = productRepository.saveAndFlush(new Product(
			otherStore, "남의상품", ProductCategory.FRUIT, 5, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/b.jpg"));

		mockMvc.perform(get("/owner/products/" + othersProduct.getId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void updateAvailableQty_toZeroWithNoActiveHolds_needsNoDisposition() throws Exception {
		Product product = createProduct("당근", 10);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/available-qty")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"availableQty":0}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableQty").value(0))
			.andExpect(jsonPath("$.data.status").value("SOLD_OUT"));
	}

	@Test
	void updateAvailableQty_toZeroWithActiveHolds_requiresDisposition() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15))));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/available-qty")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"availableQty":0}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DISPOSITION_REQUIRED"));
	}

	@Test
	void updateAvailableQty_toZeroKeepingHolds_leavesActiveHoldsUntouched() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		Hold hold = holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15))));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/available-qty")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"availableQty":0,"disposition":"KEEP_HOLDS"}"""))
			.andExpect(status().isOk());

		Hold reloaded = holdRepository.findById(hold.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(HoldStatus.HOLDING);
	}

	@Test
	void updateAvailableQty_toZeroCancelingAllHolds_cancelsThemAndNotifies() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		Hold hold = holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15))));
		long notificationsBefore = notificationRepository.count();

		String body = mockMvc.perform(patch("/owner/products/" + product.getId() + "/available-qty")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"availableQty":0,"disposition":"CANCEL_ALL"}"""))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.data.status")).isEqualTo("SOLD_OUT");

		Hold reloaded = holdRepository.findById(hold.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(HoldStatus.CANCELED);
		assertThat(notificationRepository.count()).isEqualTo(notificationsBefore + 1);
	}

	@Test
	void updateAvailableQty_aboveZero_doesNotTouchExistingHolds() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		Hold hold = holdRepository.saveAndFlush(new Hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15))));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/available-qty")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"availableQty":5}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableQty").value(5))
			.andExpect(jsonPath("$.data.status").value("ON_SALE"));

		Hold reloaded = holdRepository.findById(hold.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(HoldStatus.HOLDING);
	}
}
