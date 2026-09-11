package com.swyp.backend.home.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class OwnerHomeControllerTest {

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
		deleteAllRows();

		owner = userRepository.saveAndFlush(new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		Store newStore = new Store(
			owner, "청과마을", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
		newStore.replaceCategories(List.of(StoreCategory.VEGETABLE));
		newStore.approve();
		store = storeRepository.saveAndFlush(newStore);
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), owner.getRole().name());
	}

	@Test
	void getOwnerHome_describesTheStoreAboveTheSummary() throws Exception {
		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.store.name").value("청과마을"))
			.andExpect(jsonPath("$.data.store.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.store.categories[0]").value("VEGETABLE"))
			.andExpect(jsonPath("$.data.hasRegisteredProduct").value(false))
			.andExpect(jsonPath("$.data.products.length()").value(0))
			.andExpect(jsonPath("$.data.upcomingVisits.length()").value(0));
	}

	@Test
	void getOwnerHome_summarizesVisitsCompletedTodayAndQtyOnSale() throws Exception {
		Product carrot = createProduct("당근", 10);
		Product potato = createProduct("감자", 5);
		User consumer = createConsumer();
		carrot.hold(2);
		productRepository.saveAndFlush(carrot);
		holdRepository.saveAndFlush(new Hold(consumer, carrot, 2, Instant.now().plus(Duration.ofMinutes(15))));
		completedHold(consumer, potato, 1, Instant.now());

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.upcomingVisitCount").value(1))
			.andExpect(jsonPath("$.data.summary.completedTodayCount").value(1))
			.andExpect(jsonPath("$.data.summary.onSaleQty").value(13));
	}

	@Test
	void getOwnerHome_countsOnlyTodaysPickups() throws Exception {
		Product carrot = createProduct("당근", 10);
		User consumer = createConsumer();
		completedHold(consumer, carrot, 1, Instant.now().minus(2, ChronoUnit.DAYS));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.completedTodayCount").value(0));
	}

	@Test
	void getOwnerHome_countsOnlyTodaysExpiredHoldsAsAnIssue() throws Exception {
		Product carrot = createProduct("당근", 10);
		User consumer = createConsumer();
		carrot.hold(3);
		productRepository.saveAndFlush(carrot);
		holdRepository.saveAndFlush(new Hold(consumer, carrot, 3, Instant.now().plus(Duration.ofMinutes(15))));
		expiredHold(createConsumer(), carrot, 1, Instant.now());
		expiredHold(createConsumer(), carrot, 1, Instant.now().minus(2, ChronoUnit.DAYS));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.issues.expiredTodayCount").value(1))
			.andExpect(jsonPath("$.data.products[0].activeHoldQty").value(3))
			.andExpect(jsonPath("$.data.products[0].availableQty").value(7));
	}

	@Test
	void getOwnerHome_reportsNoIssueWhenEveryRemainingUnitIsReserved() throws Exception {
		Product carrot = createProduct("당근", 2);
		User consumer = createConsumer();
		carrot.hold(2);
		productRepository.saveAndFlush(carrot);
		holdRepository.saveAndFlush(new Hold(consumer, carrot, 2, Instant.now().plus(Duration.ofMinutes(15))));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.products[0].status").value("SOLD_OUT"))
			.andExpect(jsonPath("$.data.products[0].availableQty").value(0))
			.andExpect(jsonPath("$.data.products[0].activeHoldQty").value(2))
			.andExpect(jsonPath("$.data.issues.expiredTodayCount").value(0))
			.andExpect(jsonPath("$.data.summary.upcomingVisitCount").value(1));
	}

	@Test
	void getOwnerHome_listsHoldingVisitsSoonestFirst() throws Exception {
		Product carrot = createProduct("당근", 10);
		User late = userRepository.saveAndFlush(new User(UserRole.CONSUMER, "늦게오는손님", null, false, Instant.now()));
		User soon = userRepository.saveAndFlush(new User(UserRole.CONSUMER, "곧오는손님", null, false, Instant.now()));
		holdRepository.saveAndFlush(new Hold(late, carrot, 1, Instant.now().plus(Duration.ofMinutes(14))));
		holdRepository.saveAndFlush(new Hold(soon, carrot, 2, Instant.now().plus(Duration.ofMinutes(3))));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.upcomingVisits.length()").value(2))
			.andExpect(jsonPath("$.data.upcomingVisits[0].nickname").value("곧오는손님"))
			.andExpect(jsonPath("$.data.upcomingVisits[0].productName").value("당근"))
			.andExpect(jsonPath("$.data.upcomingVisits[0].qty").value(2))
			.andExpect(jsonPath("$.data.upcomingVisits[1].nickname").value("늦게오는손님"));
	}

	@Test
	void getOwnerHome_showsTheSellingProductsWithTheirSalePrice() throws Exception {
		createProduct("당근", 6);

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.products.length()").value(1))
			.andExpect(jsonPath("$.data.products[0].name").value("당근"))
			.andExpect(jsonPath("$.data.products[0].salePrice").value(800))
			.andExpect(jsonPath("$.data.products[0].availableQty").value(6))
			.andExpect(jsonPath("$.data.products[0].status").value("ON_SALE"))
			.andExpect(jsonPath("$.data.hasRegisteredProduct").value(true));
	}

	@Test
	void getOwnerHome_hidesProductsThatArePastPickupOrClosed() throws Exception {
		productRepository.saveAndFlush(new Product(
			store, "어제당근", ProductCategory.VEGETABLE, 10, 1000, 800,
			LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), "https://example.com/a.jpg"));
		Product closed = createProduct("판매종료감자", 5);
		closed.close();
		productRepository.saveAndFlush(closed);

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.products.length()").value(0))
			.andExpect(jsonPath("$.data.summary.onSaleQty").value(0))
			.andExpect(jsonPath("$.data.hasRegisteredProduct").value(true));
	}

	@Test
	void getOwnerHome_countsUnreadNotificationsOfTheOwnerOnly() throws Exception {
		User consumer = createConsumer();
		notificationRepository.saveAndFlush(
			new Notification(owner, NotificationType.NEW_HOLD_RECEIVED, "새로운 찜", "찜이 생겼어요", null));
		Notification read = new Notification(
			owner, NotificationType.NEW_HOLD_RECEIVED, "읽은 알림", "이미 봤어요", null);
		read.markAsRead(Instant.now());
		notificationRepository.saveAndFlush(read);
		notificationRepository.saveAndFlush(
			new Notification(consumer, NotificationType.HOLD_CREATED, "남의 알림", "남의 것", null));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.unreadNotificationCount").value(1));
	}

	@Test
	void getOwnerHome_leavesOutAnotherStoresProductsAndHolds() throws Exception {
		createProduct("당근", 10);
		User otherOwner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "다른점주", null, false, Instant.now()));
		Store otherStore = storeRepository.saveAndFlush(new Store(
			otherOwner, "다른가게", "04524", "주소", null, "0210001000",
			new BigDecimal("37.1"), new BigDecimal("127.1"), LocalTime.of(9, 0), LocalTime.of(21, 0)));
		Product othersProduct = productRepository.saveAndFlush(new Product(
			otherStore, "남의상품", ProductCategory.FRUIT, 5, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/b.jpg"));
		holdRepository.saveAndFlush(
			new Hold(createConsumer(), othersProduct, 2, Instant.now().plus(Duration.ofMinutes(15))));

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.products.length()").value(1))
			.andExpect(jsonPath("$.data.products[0].name").value("당근"))
			.andExpect(jsonPath("$.data.upcomingVisits.length()").value(0))
			.andExpect(jsonPath("$.data.summary.onSaleQty").value(10));
	}

	@Test
	void getOwnerHome_withoutAStore_isNotFound() throws Exception {
		User ownerWithoutStore = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "가게없는점주", null, false, Instant.now()));
		String otherToken = tokenProvider.createAccessToken(
			TokenRealm.USER, ownerWithoutStore.getId(), UserRole.OWNER.name());

		mockMvc.perform(get("/owner/home").header("Authorization", "Bearer " + otherToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_REGISTERED"));
	}

	@AfterEach
	void tearDown() {
		deleteAllRows();
	}

	private void deleteAllRows() {
		holdRepository.deleteAll();
		notificationRepository.deleteAll();
		productRepository.deleteAll();
		storeRepository.deleteAll();
		userRepository.deleteAll();
	}

	private Product createProduct(String name, int initialQty) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.VEGETABLE, initialQty, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private User createConsumer() {
		return userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
	}

	private void completedHold(User user, Product product, int qty, Instant completedAt) {
		Hold hold = new Hold(user, product, qty, completedAt.plus(Duration.ofMinutes(15)));
		hold.complete(completedAt);
		holdRepository.saveAndFlush(hold);
	}

	private void expiredHold(User user, Product product, int qty, Instant expiresAt) {
		Hold hold = new Hold(user, product, qty, expiresAt);
		hold.expire();
		holdRepository.saveAndFlush(hold);
	}
}
