package com.swyp.backend.hold.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.HoldFixture;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerHoldControllerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

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

	private Store store;
	private String token;

	@BeforeEach
	void setUp() {
		clearAll();

		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "청과마을사장", null, false, Instant.now()));
		store = storeRepository.saveAndFlush(new Store(
			owner, "청과마을", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
	}

	@Test
	void getOwnerHolds_listsHoldingSoonestFirst() throws Exception {
		Product product = createProduct("시금치 한 단", 10);
		holding(product, 1, Duration.ofMinutes(12));
		holding(product, 2, Duration.ofMinutes(3));

		mockMvc.perform(get("/owner/holds?status=HOLDING").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.content[0].totalQty").value(2))
			.andExpect(jsonPath("$.data.content[0].status").value("HOLDING"))
			.andExpect(jsonPath("$.data.content[0].nickname").value("윤지현"))
			.andExpect(jsonPath("$.data.content[0].items[0].productName").value("시금치 한 단"))
			.andExpect(jsonPath("$.data.content[1].totalQty").value(1));
	}

	@Test
	void getOwnerHolds_tellsAnOwnerCancelApartFromAConsumerCancel() throws Exception {
		Product product = createProduct("애호박", 10);
		Hold byOwner = holding(product, 1, Duration.ofMinutes(5));
		byOwner.cancelByOwner(Instant.now(), "재고가 모자라요");
		Hold byUser = holding(product, 2, Duration.ofMinutes(6));
		byUser.cancelByUser(Instant.now());
		holdRepository.saveAllAndFlush(java.util.List.of(byOwner, byUser));

		mockMvc.perform(get("/owner/holds?status=CANCELED_BY_OWNER").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].totalQty").value(1))
			.andExpect(jsonPath("$.data.content[0].status").value("CANCELED_BY_OWNER"));

		mockMvc.perform(get("/owner/holds?status=CANCELED_BY_USER").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].totalQty").value(2));
	}

	@Test
	void getOwnerHolds_withoutAFilter_returnsEveryStatusOfMyStoreOnly() throws Exception {
		Product product = createProduct("콩나물 한 바구니", 10);
		holding(product, 1, Duration.ofMinutes(5));
		Hold expired = holding(product, 1, Duration.ofMinutes(1));
		expired.expire();
		holdRepository.saveAndFlush(expired);
		holdOfAnotherStore();

		mockMvc.perform(get("/owner/holds").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void getOwnerHolds_paginates() throws Exception {
		Product product = createProduct("오렌지 1망", 10);
		holding(product, 1, Duration.ofMinutes(4));
		holding(product, 1, Duration.ofMinutes(5));
		holding(product, 1, Duration.ofMinutes(6));

		mockMvc.perform(get("/owner/holds?size=2").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content.length()").value(2))
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.totalPages").value(2))
			.andExpect(jsonPath("$.data.last").value(false));
	}

	@Test
	void getOwnerHold_showsTheStoreAndTheTotalPrice() throws Exception {
		Product product = createProduct("복숭아 4입", 10);
		Hold hold = holding(product, 2, Duration.ofMinutes(8));

		mockMvc.perform(get("/owner/holds/" + hold.getId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("윤지현"))
			.andExpect(jsonPath("$.data.storeName").value("청과마을"))
			.andExpect(jsonPath("$.data.totalQty").value(2))
			.andExpect(jsonPath("$.data.items[0].unitPrice").value(800))
			.andExpect(jsonPath("$.data.totalPrice").value(1600))
			.andExpect(jsonPath("$.data.status").value("HOLDING"));
	}

	@Test
	void getOwnerHold_ofAnotherStore_isNotFound() throws Exception {
		Hold othersHold = holdOfAnotherStore();

		mockMvc.perform(get("/owner/holds/" + othersHold.getId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
	}

	@Test
	void completePickup_completesTheHold_releasesTheHeldQty_andNotifiesTheConsumer() throws Exception {
		Product product = createProduct("시금치 한 단", 10);
		product.hold(3);
		productRepository.saveAndFlush(product);
		Hold hold = holding(product, 3, Duration.ofMinutes(9));

		mockMvc.perform(post("/owner/holds/" + hold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.completedAt").isNotEmpty());

		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.COMPLETED);
		Product reloaded = productRepository.findById(product.getId()).orElseThrow();
		assertThat(reloaded.getHeldQty()).isZero();
		assertThat(reloaded.getAvailableQty()).isEqualTo(7);
		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	void completePickup_twice_isRejected() throws Exception {
		Product product = createProduct("애호박", 10);
		product.hold(1);
		productRepository.saveAndFlush(product);
		Hold hold = holding(product, 1, Duration.ofMinutes(9));

		mockMvc.perform(post("/owner/holds/" + hold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(post("/owner/holds/" + hold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_RESOLVED"));

		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	void completePickup_shortlyAfterTheExpiry_isStillAccepted() throws Exception {
		Product product = createProduct("콩나물 한 바구니", 10);
		Hold hold = holding(product, 1, Duration.ofMinutes(-1));
		product.hold(1);
		productRepository.saveAndFlush(product);
		expireWithStockBack(hold, product);

		mockMvc.perform(post("/owner/holds/" + hold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(productRepository.findById(product.getId()).orElseThrow().getAvailableQty())
			.as("the goods left the shop after the batch had already put them back on the shelf")
			.isEqualTo(9);
	}

	@Test
	void completePickup_longAfterTheExpiry_isRejected() throws Exception {
		Product product = createProduct("콩나물 한 바구니", 10);
		Hold hold = holding(product, 1, Duration.ofHours(-2));
		product.hold(1);
		productRepository.saveAndFlush(product);
		expireWithStockBack(hold, product);

		mockMvc.perform(post("/owner/holds/" + hold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_RESOLVED"));
	}

	private void expireWithStockBack(Hold hold, Product product) {
		hold.expire();
		product.releaseHold(hold.getItems().getFirst().getQty());
		holdRepository.saveAndFlush(hold);
		productRepository.saveAndFlush(product);
	}

	@Test
	void completePickup_onAnotherStoresHold_isNotFound() throws Exception {
		Hold othersHold = holdOfAnotherStore();

		mockMvc.perform(post("/owner/holds/" + othersHold.getId() + "/complete")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));

		assertThat(holdRepository.findById(othersHold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
	}

	@AfterEach
	void tearDown() {
		clearAll();
	}

	private void clearAll() {
		appDataCleaner.clear();
	}

	private Product createProduct(String name, int initialQty) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.VEGETABLE, initialQty, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private Hold holding(Product product, int qty, Duration until) {
		return holdRepository.saveAndFlush(
			HoldFixture.hold(newConsumer(), product, qty, Instant.now().plus(until)));
	}

	private User newConsumer() {
		return userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "윤지현", null, false, Instant.now()));
	}

	private Hold holdOfAnotherStore() {
		User otherOwner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "다른점주", null, false, Instant.now()));
		Store otherStore = storeRepository.saveAndFlush(new Store(
			otherOwner, "다른가게", "04524", "주소", null, "0210001000",
			new BigDecimal("37.1"), new BigDecimal("127.1"), LocalTime.of(9, 0), LocalTime.of(21, 0)));
		Product othersProduct = productRepository.saveAndFlush(new Product(
			otherStore, "남의상품", ProductCategory.FRUIT, 5, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/b.jpg"));
		return holdRepository.saveAndFlush(
			HoldFixture.hold(newConsumer(), othersProduct, 1, Instant.now().plus(Duration.ofMinutes(7))));
	}
}
