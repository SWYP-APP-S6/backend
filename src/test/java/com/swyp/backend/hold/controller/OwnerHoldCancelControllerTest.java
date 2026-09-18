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
import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.repository.HoldCancelCreditEventRepository;
import com.swyp.backend.hold.repository.HoldRepository;
import com.swyp.backend.notification.DeepLinks;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.repository.NotificationRepository;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.entity.ProductCategory;
import com.swyp.backend.product.repository.ProductRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import com.swyp.backend.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerHoldCancelControllerTest {

	private static final String NOTICE =
		"[맹그로청과] 죄송합니다. 매장 재고 부족으로 인해 찜이 취소 되었습니다. 결제된 금액은 없습니다. 02-123-4567";

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
	HoldCancelCreditEventRepository holdCancelCreditEventRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private Store store;
	private String token;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();

		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "맹그로사장", null, false, Instant.now()));
		Store newStore = new Store(
			owner, "맹그로청과", "04524", "서울특별시 강남구 역삼로 1", null, "021234567",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
		newStore.approve();
		store = storeRepository.saveAndFlush(newStore);
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void getHoldCancelCandidates_listsEveryHoldOfAShortProductNewestFirst_checkingTheOnesThatDoNotFit()
			throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold pickedUp = holding(peach, "먼저온손님", 1);
		pickedUp.complete(Instant.now());
		holdRepository.saveAndFlush(pickedUp);
		Hold first = holding(peach, "윤지현", 2);
		Hold second = holding(peach, "송유나", 2);
		Hold third = holding(peach, "건우건어물", 1);
		shelve(peach, 5, 3);
		Product apple = createProduct("아오리사과 6입(특)");
		holding(apple, "맛있으면짖는개", 1);
		shelve(apple, 1, 10);

		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.productsShortOfStock").value(1))
			.andExpect(jsonPath("$.data.suggestedCancelCount").value(1))
			.andExpect(jsonPath("$.data.products.length()").value(1))
			.andExpect(jsonPath("$.data.products[0].productName").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.products[0].stockQty").value(3))
			.andExpect(jsonPath("$.data.products[0].shortfallQty").value(2))
			.andExpect(jsonPath("$.data.products[0].holds.length()").value(3))
			.andExpect(jsonPath("$.data.products[0].holds[0].holdId").value(third.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[0].heldOrder").value(4))
			.andExpect(jsonPath("$.data.products[0].holds[0].suggested").value(false))
			.andExpect(jsonPath("$.data.products[0].holds[1].holdId").value(second.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[1].heldOrder").value(3))
			.andExpect(jsonPath("$.data.products[0].holds[1].nickname").value("송유나"))
			.andExpect(jsonPath("$.data.products[0].holds[1].qty").value(2))
			.andExpect(jsonPath("$.data.products[0].holds[1].lineTotal").value(1600))
			.andExpect(jsonPath("$.data.products[0].holds[1].heldAt").isNotEmpty())
			.andExpect(jsonPath("$.data.products[0].holds[1].suggested").value(true))
			.andExpect(jsonPath("$.data.products[0].holds[2].holdId").value(first.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[2].heldOrder").value(2))
			.andExpect(jsonPath("$.data.products[0].holds[2].suggested").value(false));
	}

	@Test
	void getHoldCancelCandidates_suggestsCancellingWhateverSellsTheMostStock() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold first = holding(peach, "윤지현", 2);
		Hold second = holding(peach, "송유나", 2);
		Hold third = holding(peach, "건우건어물", 3);
		shelve(peach, 7, 5);

		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.suggestedCancelCount").value(1))
			.andExpect(jsonPath("$.data.products[0].holds[0].holdId").value(third.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[0].suggested").value(false))
			.andExpect(jsonPath("$.data.products[0].holds[1].holdId").value(second.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[1].suggested").value(true))
			.andExpect(jsonPath("$.data.products[0].holds[2].holdId").value(first.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[2].suggested").value(false));
	}

	@Test
	void getHoldCancelCandidates_previewsTheNoticeWithTheStoreNameAndPhone() throws Exception {
		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.noticeMessage").value(NOTICE));
	}

	@Test
	void getHoldCancelCandidates_whenEveryHoldFitsTheShelf_isEmpty() throws Exception {
		Product peach = createProduct("복숭아 4입");
		holding(peach, "윤지현", 2);
		shelve(peach, 2, 5);

		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.productsShortOfStock").value(0))
			.andExpect(jsonPath("$.data.suggestedCancelCount").value(0))
			.andExpect(jsonPath("$.data.products.length()").value(0));
	}

	@Test
	void getHoldCancelCandidates_leavesOutAHoldWhoseTimeIsUp() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold live = holding(peach, "윤지현", 2);
		expiredHolding(peach, "늦은손님", 2);
		shelve(peach, 4, 2);

		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.products.length()").value(1))
			.andExpect(jsonPath("$.data.products[0].shortfallQty").value(2))
			.andExpect(jsonPath("$.data.products[0].holds.length()").value(1))
			.andExpect(jsonPath("$.data.products[0].holds[0].holdId").value(live.getId()))
			.andExpect(jsonPath("$.data.products[0].holds[0].suggested").value(false))
			.andExpect(jsonPath("$.data.suggestedCancelCount").value(0));
	}

	@Test
	void getHoldCancelCandidates_whenEveryHoldOfAProductIsPastItsTime_dropsTheProduct() throws Exception {
		Product peach = createProduct("복숭아 4입");
		expiredHolding(peach, "늦은손님", 3);
		shelve(peach, 3, 1);

		candidates()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.productsShortOfStock").value(0))
			.andExpect(jsonPath("$.data.products.length()").value(0));
	}

	@Test
	void cancelHoldsForShortage_cancelsWhomeverTheOwnerPicked_andGivesTheStockBack() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold first = holding(peach, "윤지현", 2);
		Hold second = holding(peach, "송유나", 2);
		shelve(peach, 4, 2);

		cancel(first.getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.productsShortOfStock").value(0))
			.andExpect(jsonPath("$.data.products.length()").value(0));

		Hold canceled = holdRepository.findById(first.getId()).orElseThrow();
		assertThat(canceled.getStatus()).isEqualTo(HoldStatus.CANCELED);
		assertThat(canceled.getCanceledBy()).isEqualTo(HoldCanceledBy.OWNER);
		assertThat(holdRepository.findById(second.getId()).orElseThrow().getStatus())
			.as("the owner chose to keep the later customer")
			.isEqualTo(HoldStatus.HOLDING);
		Product reloaded = productRepository.findById(peach.getId()).orElseThrow();
		assertThat(reloaded.getHeldQty()).isEqualTo(2);
		assertThat(reloaded.shortfallQty()).isZero();
		List<Notification> notifications = notificationRepository.findAll();
		assertThat(notifications).hasSize(1);
		assertThat(notifications.getFirst().getType()).isEqualTo(NotificationType.HOLD_CANCELED_BY_OWNER);
		assertThat(notifications.getFirst().getBody()).isEqualTo(NOTICE);
		assertThat(notifications.getFirst().getDeepLink()).isEqualTo(DeepLinks.consumerHold(first.getId()));
		assertThat(holdCancelCreditEventRepository.count())
			.as("an owner's cancel does not spend the customer's cancel credits")
			.isZero();
	}

	@Test
	void cancelHoldsForShortage_leavesTheRestOfThatVisitHolding() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Product apple = createProduct("아오리사과 6입(특)");
		List<Hold> visit = holdRepository.saveAllAndFlush(HoldFixture.group(
			consumer("윤지현"), Instant.now().plus(Duration.ofMinutes(15)), peach, 2, apple, 1));
		shelve(peach, 2, 1);
		shelve(apple, 1, 10);

		cancel(visit.get(0).getId())
			.andExpect(status().isOk());

		assertThat(holdRepository.findById(visit.get(1).getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(productRepository.findById(apple.getId()).orElseThrow().getHeldQty()).isEqualTo(1);
	}

	@Test
	void cancelHoldsForShortage_onAProductThatIsNotShort_isRejected() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold hold = holding(peach, "윤지현", 2);
		shelve(peach, 2, 5);

		cancel(hold.getId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_SHORT_OF_STOCK"));

		assertThat(holdRepository.findById(hold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	void cancelHoldsForShortage_twice_isRejectedTheSecondTime() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold first = holding(peach, "윤지현", 2);
		holding(peach, "송유나", 2);
		shelve(peach, 4, 2);

		cancel(first.getId())
			.andExpect(status().isOk());
		cancel(first.getId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_RESOLVED"));

		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	void cancelHoldsForShortage_ofAHoldWhoseTimeIsUp_isRejected() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold overdue = expiredHolding(peach, "늦은손님", 2);
		holding(peach, "윤지현", 2);
		shelve(peach, 4, 2);

		cancel(overdue.getId())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_ALREADY_EXPIRED"));

		assertThat(holdRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
		assertThat(notificationRepository.count()).isZero();
	}

	@Test
	void cancelHoldsForShortage_withTheSameHoldTwiceInOneBody_cancelsItOnce() throws Exception {
		Product peach = createProduct("복숭아 4입");
		Hold first = holding(peach, "윤지현", 2);
		holding(peach, "송유나", 2);
		shelve(peach, 4, 2);

		cancel(first.getId(), first.getId())
			.andExpect(status().isOk());

		assertThat(productRepository.findById(peach.getId()).orElseThrow().getHeldQty())
			.as("the stock comes back once, not once per repeated id")
			.isEqualTo(2);
		assertThat(notificationRepository.count()).isEqualTo(1);
	}

	@Test
	void cancelHoldsForShortage_withMoreHoldsThanTheCap_isRejected() throws Exception {
		Long[] overTheCap = LongStream.rangeClosed(1, 101).boxed().toArray(Long[]::new);

		cancel(overTheCap)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void cancelHoldsForShortage_ofAnotherStore_isNotFound() throws Exception {
		Hold othersHold = holdOfAnotherStore();

		cancel(othersHold.getId())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));

		assertThat(holdRepository.findById(othersHold.getId()).orElseThrow().getStatus())
			.isEqualTo(HoldStatus.HOLDING);
	}

	@Test
	void cancelHoldsForShortage_withAnUnknownHold_isNotFound() throws Exception {
		cancel(Long.MAX_VALUE)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("HOLD_NOT_FOUND"));
	}

	@Test
	void cancelHoldsForShortage_withoutAnyHold_isRejected() throws Exception {
		mockMvc.perform(post("/owner/holds/cancel")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"holdIds":[]}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	private ResultActions candidates() throws Exception {
		return mockMvc.perform(get("/owner/holds/cancel-candidates")
			.header("Authorization", "Bearer " + token));
	}

	private ResultActions cancel(Long... holdIds) throws Exception {
		String ids = Arrays.stream(holdIds).map(String::valueOf).collect(Collectors.joining(","));
		return mockMvc.perform(post("/owner/holds/cancel")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"holdIds":[%s]}""".formatted(ids)));
	}

	private Product createProduct(String name) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.FRUIT, 10, 1000, 800,
			LocalDateTime.now(), LocalDateTime.now().plusHours(1), "https://example.com/a.jpg"));
	}

	private void shelve(Product product, int heldQty, int stockQty) {
		product.hold(heldQty);
		product.restock(stockQty);
		productRepository.saveAndFlush(product);
	}

	private Hold holding(Product product, String nickname, int qty) {
		return holdRepository.saveAndFlush(HoldFixture.hold(
			consumer(nickname), product, qty, Instant.now().plus(Duration.ofMinutes(15))));
	}

	private Hold expiredHolding(Product product, String nickname, int qty) {
		return holdRepository.saveAndFlush(HoldFixture.hold(
			consumer(nickname), product, qty, Instant.now().minus(Duration.ofMinutes(1))));
	}

	private User consumer(String nickname) {
		return userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, nickname, null, false, Instant.now()));
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
		othersProduct.hold(1);
		othersProduct.restock(0);
		productRepository.saveAndFlush(othersProduct);
		return holdRepository.saveAndFlush(
			HoldFixture.hold(consumer("남의손님"), othersProduct, 1, Instant.now().plus(Duration.ofMinutes(7))));
	}
}
