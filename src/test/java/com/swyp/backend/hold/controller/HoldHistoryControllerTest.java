package com.swyp.backend.hold.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.repository.HoldRepository;
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
class HoldHistoryControllerTest {

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
	JwtTokenProvider tokenProvider;

	private User consumer;
	private Store store;
	private Product peach;
	private Product tomato;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();

		consumer = newConsumer("망원동 주민");
		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "청과마을사장", null, false, Instant.now()));
		store = storeRepository.saveAndFlush(new Store(
			owner, "청과마을", "04524", "서울 마포구 망원로 12", null, "0212345678",
			new BigDecimal("37.556000"), new BigDecimal("126.901000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0)));
		peach = product("복숭아 4입", 4_000);
		tomato = product("토마토 1kg", 3_000);
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void itListsEveryHoldOfTheCallerNewestFirstAsAStoreGroup() throws Exception {
		Hold oldest = completed(peach, 1);
		Hold middle = canceled(tomato, 2);
		Hold newest = holding(peach, 2, tomato, 1);

		mockMvc.perform(get("/holds").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.serverTime").isNotEmpty())
			.andExpect(jsonPath("$.data.holds.totalElements").value(3))
			.andExpect(jsonPath("$.data.holds.content[0].id").value(newest.getId()))
			.andExpect(jsonPath("$.data.holds.content[0].status").value("HOLDING"))
			.andExpect(jsonPath("$.data.holds.content[0].storeName").value("청과마을"))
			.andExpect(jsonPath("$.data.holds.content[0].totalQty").value(3))
			.andExpect(jsonPath("$.data.holds.content[0].totalPrice").value(11_000))
			.andExpect(jsonPath("$.data.holds.content[0].items.length()").value(2))
			.andExpect(jsonPath("$.data.holds.content[0].items[0].name").value("복숭아 4입"))
			.andExpect(jsonPath("$.data.holds.content[0].items[0].lineTotal").value(8_000))
			.andExpect(jsonPath("$.data.holds.content[1].id").value(middle.getId()))
			.andExpect(jsonPath("$.data.holds.content[1].status").value("CANCELED"))
			.andExpect(jsonPath("$.data.holds.content[1].canceledBy").value("USER"))
			.andExpect(jsonPath("$.data.holds.content[2].id").value(oldest.getId()))
			.andExpect(jsonPath("$.data.holds.content[2].status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.holds.content[2].completedAt").isNotEmpty());
	}

	@Test
	void itShowsOnlyTheCallersOwnHolds() throws Exception {
		completed(peach, 1);
		User stranger = newConsumer("남의 계정");
		holdRepository.saveAndFlush(
			HoldFixture.hold(stranger, tomato, 1, Instant.now().plus(Duration.ofMinutes(10))));

		mockMvc.perform(get("/holds").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.totalElements").value(1));
	}

	@Test
	void anOverdueHoldTheBatchHasNotTouchedYetReadsAsExpired() throws Exception {
		holdRepository.saveAndFlush(
			HoldFixture.hold(consumer, peach, 1, Instant.now().minus(Duration.ofSeconds(30))));

		mockMvc.perform(get("/holds").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.content[0].status")
				.value("EXPIRED"));
	}

	@Test
	void aPageCountsHoldsRatherThanItemRows() throws Exception {
		completed(peach, 1, tomato, 1);
		completed(peach, 1, tomato, 1);
		completed(peach, 1, tomato, 1);

		mockMvc.perform(get("/holds?size=2").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.content.length()").value(2))
			.andExpect(jsonPath("$.data.holds.content[0].items.length()").value(2))
			.andExpect(jsonPath("$.data.holds.content[1].items.length()").value(2))
			.andExpect(jsonPath("$.data.holds.totalElements").value(3))
			.andExpect(jsonPath("$.data.holds.totalPages").value(2))
			.andExpect(jsonPath("$.data.holds.last").value(false));
	}

	@Test
	void aPageBeyondTheLastOneStillReportsTheRealTotal() throws Exception {
		completed(peach, 1);

		mockMvc.perform(get("/holds?page=5&size=20").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.content.length()").value(0))
			.andExpect(jsonPath("$.data.holds.totalElements").value(1));
	}

	@Test
	void anOversizedPageIsCappedRatherThanFetchedWhole() throws Exception {
		completed(peach, 1);

		mockMvc.perform(get("/holds?size=5000").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.size")
				.value(100));
	}

	@Test
	void aConsumerWhoNeverHeldAnythingGetsAnEmptyPage() throws Exception {
		mockMvc.perform(get("/holds").header("Authorization", bearer(consumer)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.holds.content.length()").value(0))
			.andExpect(jsonPath("$.data.holds.totalElements").value(0));
	}

	@Test
	void anOwnerTokenCannotReadTheConsumerHistory() throws Exception {
		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "점주", null, false, Instant.now()));

		mockMvc.perform(get("/holds").header("Authorization", bearer(owner)))
			.andExpect(status().isForbidden());
	}

	private String bearer(User user) {
		return "Bearer " + tokenProvider.createAccessToken(
			TokenRealm.USER, user.getId(), user.getRole().name());
	}

	private User newConsumer(String nickname) {
		return userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, nickname, null, false, Instant.now()));
	}

	private Product product(String name, int salePrice) {
		return productRepository.saveAndFlush(new Product(
			store, name, ProductCategory.FRUIT, 10, salePrice * 2, salePrice,
			LocalDateTime.now(), LocalDateTime.now().plusHours(3), "https://example.com/a.jpg"));
	}

	private Hold holding(Product first, int firstQty, Product second, int secondQty) {
		return holdRepository.saveAndFlush(HoldFixture.hold(
			consumer, Instant.now().plus(Duration.ofMinutes(10)), first, firstQty, second, secondQty));
	}

	private Hold completed(Product product, int qty) {
		Hold hold = HoldFixture.hold(consumer, product, qty, Instant.now().plus(Duration.ofMinutes(10)));
		hold.complete(Instant.now());
		return holdRepository.saveAndFlush(hold);
	}

	private Hold completed(Product first, int firstQty, Product second, int secondQty) {
		Hold hold = HoldFixture.hold(
			consumer, Instant.now().plus(Duration.ofMinutes(10)), first, firstQty, second, secondQty);
		hold.complete(Instant.now());
		return holdRepository.saveAndFlush(hold);
	}

	private Hold canceled(Product product, int qty) {
		Hold hold = HoldFixture.hold(consumer, product, qty, Instant.now().plus(Duration.ofMinutes(10)));
		hold.cancelByUser(Instant.now());
		return holdRepository.saveAndFlush(hold);
	}
}
