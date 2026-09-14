package com.swyp.backend.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.ClockConfig;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.PhotoFixture;
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
import java.time.temporal.ChronoUnit;
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
	private String photoUrl;

	@BeforeEach
	void setUp() throws Exception {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();

		owner = userRepository.saveAndFlush(new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		store = storeRepository.saveAndFlush(new Store(
			owner, "테스트가게", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), owner.getRole().name());
		photoUrl = PhotoFixture.uploadedPhotoUrl(mockMvc, token);
	}

	private String registerBody(String name, int originalPrice, int salePrice) {
		return """
			{"name":"%s","category":"VEGETABLE","initialQty":10,"originalPrice":%d,"salePrice":%d,\
			"photoUrl":"%s","ingredientTags":[]}"""
				.formatted(name, originalPrice, salePrice, photoUrl);
	}

	private String registerBodyWithPickupEndAt(LocalDateTime pickupEndAt) {
		return """
			{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,"salePrice":800,\
			"photoUrl":"%s","pickupEndAt":"%s"}""".formatted(photoUrl, pickupEndAt);
	}

	@Test
	void updateStock_countsWhatIsInTheShop_notWhatIsLeftOverTheHolds() throws Exception {
		Product product = createProduct("당근", 10);
		holdRepository.saveAndFlush(
			HoldFixture.hold(createConsumer(), product, 3, Instant.now().plus(Duration.ofMinutes(15))));
		product.hold(3);
		productRepository.saveAndFlush(product);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":8,"cancelOverflow":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableQty").value(5))
			.andExpect(jsonPath("$.data.heldQty").value(3))
			.andExpect(jsonPath("$.data.shortfallQty").value(0));
	}

	@Test
	void updateStock_belowSixtyPercentOfWhatWasRegistered_isRejected() throws Exception {
		Product product = createProduct("당근", 10);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":5,"cancelOverflow":false}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("QTY_BELOW_MINIMUM"));
	}

	@Test
	void updateStock_belowSixtyPercent_opensUpOnceTheReconfirmWasAsked() throws Exception {
		Product product = createProduct("당근", 10);
		product.markReconfirmSent(Instant.now());
		productRepository.saveAndFlush(product);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":0,"cancelOverflow":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableQty").value(0))
			.andExpect(jsonPath("$.data.status").value("SOLD_OUT"));
	}

	@Test
	void updateStock_leavingTheOverflowAlone_keepsTheHoldsAndReportsTheShortfall() throws Exception {
		Product product = askedToReconfirm("당근", 10);
		holdRepository.saveAndFlush(
			HoldFixture.hold(createConsumer(), product, 3, Instant.now().plus(Duration.ofMinutes(15))));
		product.hold(3);
		productRepository.saveAndFlush(product);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":1,"cancelOverflow":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.heldQty").value(3))
			.andExpect(jsonPath("$.data.availableQty").value(0))
			.andExpect(jsonPath("$.data.shortfallQty").value(2));

		assertThat(holdRepository.findAll())
			.as("BR-015: a shortfall never cancels on its own")
			.allMatch(hold -> hold.getStatus() == HoldStatus.HOLDING);
	}

	@Test
	void updateStock_cancelingTheOverflow_keepsWhoeverHeldFirst() throws Exception {
		Product product = askedToReconfirm("당근", 10);
		Hold first = holdWithQty(product, 1, Duration.ofMinutes(15));
		Hold second = holdWithQty(product, 2, Duration.ofMinutes(14));
		Hold third = holdWithQty(product, 1, Duration.ofMinutes(13));
		long notificationsBefore = notificationRepository.count();

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":1,"cancelOverflow":true}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.heldQty").value(1))
			.andExpect(jsonPath("$.data.availableQty").value(0))
			.andExpect(jsonPath("$.data.shortfallQty").value(0));

		assertThat(holdRepository.findById(first.getId()).orElseThrow().getStatus())
			.as("the customer who held first keeps the one unit that exists")
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(holdRepository.findById(second.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.CANCELED);
		assertThat(holdRepository.findById(third.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.CANCELED);
		assertThat(notificationRepository.count()).isEqualTo(notificationsBefore + 2);
	}

	@Test
	void stockReconfirm_sayingTheQuantityIsRight_locksItUntilPickupCloses() throws Exception {
		Product product = askedToReconfirm("당근", 10);

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":true}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockEditable").value(false))
			.andExpect(jsonPath("$.data.reconfirmPending").value(false));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":6,"cancelOverflow":false}"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STOCK_LOCKED"));
	}

	@Test
	void stockReconfirm_sayingItIsWrong_leavesTheQuantityEditable() throws Exception {
		Product product = askedToReconfirm("당근", 10);

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockEditable").value(true))
			.andExpect(jsonPath("$.data.minAdjustableQty").value(0));
	}

	@Test
	void stockReconfirm_onAProductNobodyWasAskedAbout_isRejected() throws Exception {
		Product product = createProduct("당근", 10);

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":true}"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECONFIRM_NOT_REQUESTED"));
	}

	@Test
	void updateStock_onAClosedProduct_isRejected() throws Exception {
		Product product = createProduct("당근", 10);
		product.close();
		productRepository.saveAndFlush(product);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":8,"cancelOverflow":false}"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_CLOSED"));

		assertThat(productRepository.findById(product.getId()).orElseThrow().getAvailableQty())
			.isEqualTo(10);
	}

	@Test
	void stockReconfirm_answeredTwice_isRejected() throws Exception {
		Product product = askedToReconfirm("당근", 10);

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":false}"""))
			.andExpect(status().isOk());

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":true}"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECONFIRM_NOT_REQUESTED"));

		mockMvc.perform(get("/owner/products/" + product.getId())
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockEditable").value(true));
	}

	@Test
	void stockReconfirm_confirmingIt_stopsLockingOncePickupHasClosed() throws Exception {
		Product product = productRepository.saveAndFlush(new Product(
			store, "지난 당근", ProductCategory.VEGETABLE, 10, 1000, 800,
			LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(1),
			"https://example.com/a.jpg"));
		product.markReconfirmSent(Instant.now());
		product.confirmStock(Instant.now());
		productRepository.saveAndFlush(product);

		mockMvc.perform(get("/owner/products/" + product.getId())
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockEditable").value(true));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":4,"cancelOverflow":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockQty").value(4));
	}

	@Test
	void updateStock_beyondTheCap_isRejected() throws Exception {
		Product product = askedToReconfirm("당근", 10);

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":2147483647,"cancelOverflow":false}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void updateStock_withoutTheQuantity_isRejectedAndCancelsNothing() throws Exception {
		Product product = askedToReconfirm("당근", 10);
		Hold hold = holdWithQty(product, 2, Duration.ofMinutes(15));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"cancelOverflow":true}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":0}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQty())
			.isEqualTo(10);
	}

	@Test
	void updateStock_afterPickupsTookTheShelfBelowTheFloor_stillSavesWhatIsLeft() throws Exception {
		Product product = createProduct("당근", 10);
		product.hold(5);
		product.completeHold(5);
		productRepository.saveAndFlush(product);

		mockMvc.perform(get("/owner/products/" + product.getId())
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.minAdjustableQty").value(5));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":5,"cancelOverflow":false}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stockQty").value(5));

		mockMvc.perform(patch("/owner/products/" + product.getId() + "/stock")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"stockQty":4,"cancelOverflow":false}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("QTY_BELOW_MINIMUM"));
	}

	@Test
	void stockReconfirm_whileTheHoldsOutrunTheShelf_isRejected() throws Exception {
		Product product = askedToReconfirm("당근", 10);
		product.hold(6);
		product.restock(4);
		productRepository.saveAndFlush(product);

		mockMvc.perform(post("/owner/products/" + product.getId() + "/stock-reconfirm")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"confirmed":true}"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STOCK_SHORT_OF_HOLDS"));

		mockMvc.perform(get("/owner/products/" + product.getId())
				.header("Authorization", "Bearer " + token))
			.andExpect(jsonPath("$.data.stockEditable").value(true))
			.andExpect(jsonPath("$.data.shortfallQty").value(2));
	}

	private Product askedToReconfirm(String name, int initialQty) {
		Product product = createProduct(name, initialQty);
		product.markReconfirmSent(Instant.now());
		return productRepository.saveAndFlush(product);
	}

	private Hold holdWithQty(Product product, int qty, Duration until) {
		Hold hold = holdRepository.saveAndFlush(
			HoldFixture.hold(createConsumer(), product, qty, Instant.now().plus(until)));
		product.hold(qty);
		productRepository.saveAndFlush(product);
		return hold;
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
	void previewProduct_showsTheCardTheConsumerWillSee() throws Exception {
		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("복숭아 4입", 10000, 4000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.salePrice").value(4000))
			.andExpect(jsonPath("$.data.originalPrice").value(10000))
			.andExpect(jsonPath("$.data.discountRate").value(60))
			.andExpect(jsonPath("$.data.initialQty").value(10))
			.andExpect(jsonPath("$.data.category").value("VEGETABLE"));
	}

	@Test
	void previewProduct_registersNothing() throws Exception {
		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("복숭아 4입", 10000, 4000)))
			.andExpect(status().isOk());

		assertThat(productRepository.count()).isZero();
	}

	@Test
	void previewProduct_fillsThePickupEndFromTheStoreClosingTime() throws Exception {
		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("복숭아 4입", 10000, 4000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pickupEndAt").value(endsWith("21:00:00")));
	}

	@Test
	void previewProduct_keepsAPickupEndTheOwnerChose() throws Exception {
		LocalDateTime chosen = LocalDateTime.now().plusHours(2).withNano(0).withSecond(0);

		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBodyWithPickupEndAt(chosen)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pickupEndAt").value(startsWith(chosen.toString())));
	}

	@Test
	void previewProduct_rejectsWhatRegisteringWouldReject() throws Exception {
		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("복숭아 4입", 10000, 10000)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PRICE"));

		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBodyWithPickupEndAt(LocalDateTime.now().plusDays(2))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PICKUP_WINDOW"));
	}

	@Test
	void previewProduct_withoutAStore_isRejected() throws Exception {
		User ownerWithoutStore = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "가게없는점주", null, false, Instant.now()));
		String tokenWithoutStore =
			tokenProvider.createAccessToken(TokenRealm.USER, ownerWithoutStore.getId(), UserRole.OWNER.name());

		mockMvc.perform(post("/owner/products/preview")
				.header("Authorization", "Bearer " + tokenWithoutStore)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBody("복숭아 4입", 10000, 4000)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_REGISTERED"));
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
	void registerProduct_withoutACategory_defaultsToEtc() throws Exception {
		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","initialQty":10,"originalPrice":1000,"salePrice":800,\
					"photoUrl":"%s"}""".formatted(photoUrl)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.category").value("ETC"));
	}

	@Test
	void registerProduct_withAnExplicitPickupEndAt_overridesTheStoreDefault() throws Exception {
		LocalDateTime pickupEndAt = LocalDateTime.now(ClockConfig.SERVICE_ZONE)
			.plusHours(2).truncatedTo(ChronoUnit.SECONDS);

		String body = mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBodyWithPickupEndAt(pickupEndAt)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		long productId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
		assertThat(productRepository.findById(productId).orElseThrow().getPickupEndAt()).isEqualTo(pickupEndAt);
	}

	@Test
	void registerProduct_withAPickupEndAtInThePast_isRejected() throws Exception {
		LocalDateTime pastPickupEndAt = LocalDateTime.now(ClockConfig.SERVICE_ZONE)
			.minusHours(1).truncatedTo(ChronoUnit.SECONDS);

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBodyWithPickupEndAt(pastPickupEndAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PICKUP_WINDOW"));
	}

	@Test
	void registerProduct_withAPickupEndAtBeyond24Hours_isRejected() throws Exception {
		LocalDateTime tooLatePickupEndAt = LocalDateTime.now(ClockConfig.SERVICE_ZONE)
			.plusHours(25).truncatedTo(ChronoUnit.SECONDS);

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerBodyWithPickupEndAt(tooLatePickupEndAt)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PICKUP_WINDOW"));
	}

	@Test
	void registerProduct_withAnUnknownIngredientTag_isRejected() throws Exception {
		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,"salePrice":800,\
					"photoUrl":"%s","ingredientTags":[999999]}""".formatted(photoUrl)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
	}

	@Test
	void registerProduct_withAPhotoUrlLongerThanTheColumnLimit_isRejected() throws Exception {
		String tooLongPhotoUrl = "https://example.com/" + "a".repeat(500);

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,"salePrice":800,\
					"photoUrl":"%s"}""".formatted(tooLongPhotoUrl)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
	void getMyProduct_includesCompletedQty() throws Exception {
		Product product = createProduct("당근", 10);
		User consumer = createConsumer();
		Hold completedHold = HoldFixture.hold(consumer, product, 3, Instant.now().plus(Duration.ofMinutes(15)));
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
			otherOwner, "다른가게", "04524", "주소", null, "0210001000",
			new BigDecimal("37.1"), new BigDecimal("127.1"), LocalTime.of(9, 0), LocalTime.of(21, 0)));
		Product othersProduct = productRepository.saveAndFlush(new Product(
			otherStore, "남의상품", ProductCategory.FRUIT, 5, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/b.jpg"));

		mockMvc.perform(get("/owner/products/" + othersProduct.getId()).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}






}
