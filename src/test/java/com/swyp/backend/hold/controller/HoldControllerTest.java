package com.swyp.backend.hold.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@Transactional
class HoldControllerTest {

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

	private User consumer;
	private Product product;

	@BeforeEach
	void setUp() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();

		consumer = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		product = sellableProduct(3, LocalDateTime.now().plusHours(5));
	}

	private Product sellableProduct(int qty, LocalDateTime pickupEndAt) {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주" + qty + pickupEndAt.getNano(), null, false, Instant.now()));
		Store store = new Store(
				owner, "청과마을", "04524", "서울 마포구 망원로 12", "1층", "02-1234-5678",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceCategories(Set.of(StoreCategory.FRUIT));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		store.approve();
		storeRepository.saveAndFlush(store);
		return productRepository.saveAndFlush(new Product(
				store, "복숭아 4입", ProductCategory.FRUIT, qty, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/peach.jpg"));
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
				TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private static String body(Long productId, int qty) {
		return "{\"productId\":%d,\"qty\":%d}".formatted(productId, qty);
	}

	@Test
	void holdingMovesTheQuantityOutOfAvailableAndAnswersWithTheCountdownFields() throws Exception {
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 2)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("HOLDING"))
			.andExpect(jsonPath("$.data.qty").value(2))
			.andExpect(jsonPath("$.data.unitPrice").value(4000))
			.andExpect(jsonPath("$.data.totalPrice").value(8000))
			.andExpect(jsonPath("$.data.expiresAt").exists())
			.andExpect(jsonPath("$.data.serverTime").exists())
			.andExpect(jsonPath("$.data.product.name").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.store.name").value("청과마을"));

		Product held = productRepository.findById(product.getId()).orElseThrow();
		assertThat(held.getAvailableQty()).isEqualTo(1);
		assertThat(held.getHeldQty()).isEqualTo(2);
	}

	@Test
	void holdingEverythingLeftSellsTheProductOut() throws Exception {
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 3)))
			.andExpect(status().isCreated());

		assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
			.isEqualTo(com.swyp.backend.product.entity.ProductStatus.SOLD_OUT);
	}

	@Test
	void theExpiryNeverOutlivesThePickupDeadline() throws Exception {
		Product closingSoon = sellableProduct(2, LocalDateTime.now().plusMinutes(4));

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(closingSoon.getId(), 1)))
			.andExpect(status().isCreated());

		Hold hold = holdRepository.findAll().stream()
			.filter(h -> h.getProduct().getId().equals(closingSoon.getId()))
			.findFirst().orElseThrow();
		assertThat(hold.getExpiresAt())
			.as("a hold that outlives the pickup window walks the user to a closed shop, and one "
					+ "cut shorter than the window throws away sellable minutes")
			.isBetween(Instant.now().plusSeconds(3 * 60 + 30), Instant.now().plusSeconds(4 * 60 + 30));
	}

	@Test
	void aDistantPickupDeadlineLeavesTheFullFifteenMinutes() throws Exception {
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 1)))
			.andExpect(status().isCreated());

		Hold hold = holdRepository.findAll().getFirst();
		assertThat(hold.getExpiresAt())
			.as("the pickup window is five hours out, so the ttl is what binds")
			.isBetween(Instant.now().plusSeconds(14 * 60), Instant.now().plusSeconds(15 * 60 + 30));
	}

	@Test
	void takingMoreThanIsLeftOnAProductStillOnSaleIsAConflict() throws Exception {
		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "먼저찜한소비자", null, false, Instant.now()));
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(other))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 2)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 2)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code")
				.value("INSUFFICIENT_QTY"));

		Product held = productRepository.findById(product.getId()).orElseThrow();
		assertThat(held.getAvailableQty()).isEqualTo(1);
		assertThat(held.getHeldQty()).isEqualTo(2);
	}

	@Test
	void aProductPastItsPickupDeadlineCannotBeHeld() throws Exception {
		Product stale = sellableProduct(2, LocalDateTime.now().minusMinutes(30));

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(stale.getId(), 1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));

		assertThat(productRepository.findById(stale.getId()).orElseThrow().getHeldQty())
			.as("a hold born expired moves stock nobody can collect")
			.isZero();
	}

	@Test
	void aProductOfAnUnapprovedStoreCannotBeHeld() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "미승인점주", null, false, Instant.now()));
		Store pending = new Store(
				owner, "미승인가게", "04524", "서울 마포구 망원로 99", null, "02-9999-9999",
				new BigDecimal("37.556000"), new BigDecimal("126.901000"),
				LocalTime.of(9, 0), LocalTime.of(21, 0));
		pending.replaceCategories(Set.of(StoreCategory.FRUIT));
		pending.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		storeRepository.saveAndFlush(pending);
		LocalDateTime pickupEndAt = LocalDateTime.now().plusHours(3);
		Product hidden = productRepository.saveAndFlush(new Product(
				pending, "숨은 상품", ProductCategory.FRUIT, 5, 10_000, 4_000,
				pickupEndAt.minusHours(1), pickupEndAt, "https://cdn.example.com/x.jpg"));

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(hidden.getId(), 1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));
	}

	@Test
	void askingForMoreThanIsLeftIsRejectedWithoutTakingAny() throws Exception {
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 3)))
			.andExpect(status().isCreated());

		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(other))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));

		Product held = productRepository.findById(product.getId()).orElseThrow();
		assertThat(held.getAvailableQty()).isZero();
		assertThat(held.getHeldQty()).isEqualTo(3);
	}

	@Test
	void aSecondHoldOnTheSameProductIsRejected() throws Exception {
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 1)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_HOLDING"));
	}

	@Test
	void aQuantityAboveTheLimitIsRejected() throws Exception {
		Product plenty = sellableProduct(20, LocalDateTime.now().plusHours(5));

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(plenty.getId(), 4)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("HOLD_LIMIT_EXCEEDED"));
	}

	@Test
	void holdingIsClosedToGuestsAndOwners() throws Exception {
		String content = body(product.getId(), 1);
		mockMvc.perform(post("/holds")
				.header("Authorization", "Bearer " + tokenProvider.createAccessToken(
					TokenRealm.GUEST, 1L, "GUEST"))
				.contentType(MediaType.APPLICATION_JSON).content(content))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

		User owner = userRepository.saveAndFlush(
				new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(owner))
				.contentType(MediaType.APPLICATION_JSON).content(content))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(post("/holds")
				.contentType(MediaType.APPLICATION_JSON).content(content))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anExpiredHoldIsClearedSoTheSameProductCanBeHeldAgain() throws Exception {
		Hold overdue = holdRepository.saveAndFlush(
				new Hold(consumer, product, 1, Instant.now().minusSeconds(60)));
		product.hold(1);
		productRepository.saveAndFlush(product);

		mockMvc.perform(post("/holds")
				.header("Authorization", bearer(consumer))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(product.getId(), 1)))
			.andExpect(status().isCreated());

		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.as("the partial unique index would block the new row while the stale one is HOLDING")
			.isEqualTo(HoldStatus.EXPIRED);
		Product held = productRepository.findById(product.getId()).orElseThrow();
		assertThat(held.getAvailableQty()).isEqualTo(2);
		assertThat(held.getHeldQty()).isEqualTo(1);
	}

	@Test
	void cancelingMyHoldGivesTheQuantityBackAndRecordsWhoCanceled() throws Exception {
		Hold hold = holding(consumer, 2, Instant.now().plusSeconds(600));

		mockMvc.perform(post("/holds/{holdId}/cancel", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(hold.getId()))
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.canceledBy").value("USER"))
			.andExpect(jsonPath("$.data.canceledAt").exists())
			.andExpect(jsonPath("$.data.qty").value(2));

		Product released = productRepository.findById(product.getId()).orElseThrow();
		assertThat(released.getAvailableQty()).isEqualTo(3);
		assertThat(released.getHeldQty()).isZero();
	}

	@Test
	void cancelingTheSameHoldTwiceGivesTheQuantityBackOnce() throws Exception {
		Hold hold = holding(consumer, 2, Instant.now().plusSeconds(600));
		mockMvc.perform(post("/holds/{holdId}/cancel", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/holds/{holdId}/cancel", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_RESOLVED"));

		Product released = productRepository.findById(product.getId()).orElseThrow();
		assertThat(released.getAvailableQty()).isEqualTo(3);
		assertThat(released.getHeldQty()).isZero();
	}

	@Test
	void someoneElsesHoldIsNotFoundRatherThanForbidden() throws Exception {
		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		Hold hold = holding(other, 2, Instant.now().plusSeconds(600));

		mockMvc.perform(post("/holds/{holdId}/cancel", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));

		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		Product untouched = productRepository.findById(product.getId()).orElseThrow();
		assertThat(untouched.getAvailableQty()).isEqualTo(1);
		assertThat(untouched.getHeldQty()).isEqualTo(2);
	}

	@Test
	void aHoldThatIsAlreadyPastItsExpiryCannotBeCanceled() throws Exception {
		Hold overdue = holding(consumer, 2, Instant.now().minusSeconds(1));

		mockMvc.perform(post("/holds/{holdId}/cancel", overdue.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_EXPIRED"));

		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.as("the expiry batch owns the transition, so a late cancel must not record CANCELED")
			.isEqualTo(HoldStatus.HOLDING);
		Product untouched = productRepository.findById(product.getId()).orElseThrow();
		assertThat(untouched.getAvailableQty()).isEqualTo(1);
		assertThat(untouched.getHeldQty()).isEqualTo(2);
	}

	@Test
	void cancelingIsClosedToGuests() throws Exception {
		Hold hold = holding(consumer, 1, Instant.now().plusSeconds(600));

		mockMvc.perform(post("/holds/{holdId}/cancel", hold.getId())
				.header("Authorization", "Bearer " + tokenProvider.createAccessToken(
					TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));

		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
	}

	@Test
	void readingMyHoldAnswersWithTheCountdownFieldsAndTheStore() throws Exception {
		Hold hold = holding(consumer, 2, Instant.now().plusSeconds(600));

		mockMvc.perform(get("/holds/{holdId}", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(hold.getId()))
			.andExpect(jsonPath("$.data.status").value("HOLDING"))
			.andExpect(jsonPath("$.data.qty").value(2))
			.andExpect(jsonPath("$.data.totalPrice").value(8000))
			.andExpect(jsonPath("$.data.heldAt").exists())
			.andExpect(jsonPath("$.data.expiresAt").exists())
			.andExpect(jsonPath("$.data.serverTime").exists())
			.andExpect(jsonPath("$.data.product.name").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.store.name").value("청과마을"));
	}

	@Test
	void aHoldPastItsExpiryReadsAsExpiredWithoutBeingWrittenTo() throws Exception {
		Hold overdue = holding(consumer, 2, Instant.now().minusSeconds(1));

		mockMvc.perform(get("/holds/{holdId}", overdue.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("EXPIRED"));

		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.as("a read must not transition the row -- the batch restores the stock with it")
			.isEqualTo(HoldStatus.HOLDING);
		Product untouched = productRepository.findById(product.getId()).orElseThrow();
		assertThat(untouched.getAvailableQty()).isEqualTo(1);
		assertThat(untouched.getHeldQty()).isEqualTo(2);
	}

	@Test
	void readingSomeoneElsesHoldIsNotFound() throws Exception {
		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		Hold hold = holding(other, 1, Instant.now().plusSeconds(600));

		mockMvc.perform(get("/holds/{holdId}", hold.getId())
				.header("Authorization", bearer(consumer)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
	}

	@Test
	void activeHoldsComeBackSoonestFirstAndLeaveOutTheOnesThatAreDone() throws Exception {
		Product other = sellableProduct(5, LocalDateTime.now().plusHours(6));
		Hold later = holding(consumer, product, 1, Instant.now().plusSeconds(900));
		Hold sooner = holding(consumer, other, 1, Instant.now().plusSeconds(300));
		holding(consumer, sellableProduct(5, LocalDateTime.now().plusHours(7)), 1,
				Instant.now().minusSeconds(1));
		Hold canceled = holding(consumer, sellableProduct(5, LocalDateTime.now().plusHours(8)), 1,
				Instant.now().plusSeconds(600));
		canceled.cancelByUser(Instant.now());
		holdRepository.saveAndFlush(canceled);

		mockMvc.perform(get("/holds/active").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(sooner.getId()))
			.andExpect(jsonPath("$.data[1].id").value(later.getId()));
	}

	@Test
	void activeHoldsOfSomeoneElseAreNotListed() throws Exception {
		User other = userRepository.saveAndFlush(
				new User(UserRole.CONSUMER, "다른소비자", null, false, Instant.now()));
		holding(other, 1, Instant.now().plusSeconds(600));

		mockMvc.perform(get("/holds/active").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));

		mockMvc.perform(get("/holds/active")
				.header("Authorization", "Bearer " + tokenProvider.createAccessToken(
					TokenRealm.GUEST, 1L, "GUEST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
	}

	private Hold holding(User user, int qty, Instant expiresAt) {
		return holding(user, product, qty, expiresAt);
	}

	private Hold holding(User user, Product target, int qty, Instant expiresAt) {
		target.hold(qty);
		productRepository.saveAndFlush(target);
		return holdRepository.saveAndFlush(new Hold(user, target, qty, expiresAt));
	}
}
