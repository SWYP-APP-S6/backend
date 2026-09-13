package com.swyp.backend.hold.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.hold.HoldFixture;
import com.swyp.backend.hold.HoldProperties;
import com.swyp.backend.hold.dto.HoldCreateRequest;
import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.hold.repository.HoldCancelCreditEventRepository;
import com.swyp.backend.hold.repository.HoldCancelCreditRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class HoldCancelCreditLedgerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	HoldService holdService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	HoldRepository holdRepository;

	@Autowired
	HoldCancelCreditRepository creditRepository;

	@Autowired
	HoldCancelCreditEventRepository eventRepository;

	@Autowired
	HoldProperties holdProperties;

	// 시간과 잔액을 앞당겨 두는 건 테스트의 사정이다. 그걸 위해 프로덕션 리포지토리에 메서드를
	// 만들면, 아무도 부르지 않는 코드가 본체에 남는다.
	@Autowired
	JdbcTemplate jdbcTemplate;

	private User consumer;
	private Store store;
	private Product product;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		consumer = userRepository.saveAndFlush(
			new User(UserRole.CONSUMER, "소비자", null, false, Instant.now()));
		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "점주", null, false, Instant.now()));
		Store created = new Store(
			owner, "청과마을", "04524", "서울 마포구 망원로 12", null, "0212345678",
			new BigDecimal("37.556000"), new BigDecimal("126.901000"),
			LocalTime.MIN, LocalTime.MAX);
		created.replaceCategories(Set.of(StoreCategory.FRUIT));
		created.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		created.approve();
		store = storeRepository.saveAndFlush(created);
		product = productRepository.saveAndFlush(new Product(
			store, "복숭아 4입", ProductCategory.FRUIT, 10, 10_000, 4_000,
			LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5),
			"https://cdn.example.com/peach.jpg"));
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	private Hold hold(int qty) {
		return holdRepository.findById(
				holdService.create(consumer.getId(), new HoldCreateRequest(product.getId(), qty)).id())
			.orElseThrow();
	}

	private void backdateCreation(Long holdId) {
		jdbcTemplate.update(
			"update holds set created_at = now() - interval '10 minutes' where id = ?", holdId);
	}

	private void emptyCredits() {
		jdbcTemplate.update(
			"update hold_cancel_credits set credits = 0 where user_id = ?", consumer.getId());
	}

	@Test
	void cancellingAHoldOfManyItemsCostsOneCreditAndLeavesOneRow() {
		Product second = productRepository.saveAndFlush(new Product(
			store, "토마토 1kg", ProductCategory.FRUIT, 10, 8_000, 4_000,
			LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(5),
			"https://cdn.example.com/tomato.jpg"));
		Hold hold = hold(3);
		holdService.create(consumer.getId(), new HoldCreateRequest(second.getId(), 2));
		backdateCreation(hold.getId());

		holdService.cancel(consumer.getId(), hold.getId());

		assertThat(creditRepository.findByUserId(consumer.getId()).orElseThrow().getCredits())
			.isEqualTo(holdProperties.cancelCreditMax() - 1);
		assertThat(eventRepository.findAll())
			.extracting(HoldCancelCreditEvent::getReason, HoldCancelCreditEvent::getDelta)
			.containsExactly(tuple(HoldCancelCreditReason.CANCEL, (short) -1));
	}

	@Test
	void cancellingInsideTheMisTapWindowLeavesNoRow() {
		Hold hold = hold(1);

		holdService.cancel(consumer.getId(), hold.getId());

		assertThat(creditRepository.findByUserId(consumer.getId()).orElseThrow().getCredits())
			.isEqualTo(holdProperties.cancelCreditMax());
		assertThat(eventRepository.findAll()).isEmpty();
	}

	@Test
	void aNoShowIsChargedWhenTheNextHoldIsMadeAndPointsAtTheHoldItCameFrom() {
		Hold missed = holdRepository.saveAndFlush(
			HoldFixture.hold(consumer, product, 1, Instant.now().minus(Duration.ofHours(3))));
		missed.expire();
		holdRepository.saveAndFlush(missed);

		holdService.create(consumer.getId(), new HoldCreateRequest(product.getId(), 1));

		List<HoldCancelCreditEvent> events = eventRepository.findAll();
		assertThat(events)
			.extracting(HoldCancelCreditEvent::getReason, HoldCancelCreditEvent::getDelta)
			.containsExactly(tuple(HoldCancelCreditReason.NO_SHOW, (short) -1));
		assertThat(events.getFirst().getHold().getId()).isEqualTo(missed.getId());
	}

	@Test
	void cancellingWithNothingLeftToSpendLeavesNoRow() {
		Hold hold = hold(1);
		backdateCreation(hold.getId());
		emptyCredits();

		holdService.cancel(consumer.getId(), hold.getId());

		assertThat(creditRepository.findByUserId(consumer.getId()).orElseThrow().getCredits())
			.as("취소 자체는 잔액이 없어도 막지 않는다 — 재고를 돌려주는 행동이다")
			.isZero();
		assertThat(eventRepository.findAll()).isEmpty();
	}

	@Test
	void readingTheActiveHoldNeverWritesToTheLedger() {
		Hold missed = holdRepository.saveAndFlush(
			HoldFixture.hold(consumer, product, 1, Instant.now().minus(Duration.ofHours(3))));
		missed.expire();
		holdRepository.saveAndFlush(missed);

		holdService.getActiveHold(consumer.getId());
		holdService.getActiveHold(consumer.getId());

		assertThat(eventRepository.findAll())
			.as("조회는 표시용으로만 계산한다 — 부를 때마다 이력이 쌓이면 안 된다")
			.isEmpty();
	}

	@Test
	void theHistoryRowSaysWhetherThatHoldCostACredit() {
		Hold charged = hold(1);
		backdateCreation(charged.getId());
		holdService.cancel(consumer.getId(), charged.getId());
		Hold free = hold(1);
		holdService.cancel(consumer.getId(), free.getId());

		var page = holdService.getHolds(
			consumer.getId(), org.springframework.data.domain.PageRequest.of(0, 10));

		assertThat(page.holds().content())
			.extracting(row -> row.id(), row -> row.cancelCreditUsed())
			.containsExactly(tuple(free.getId(), false), tuple(charged.getId(), true));
	}
}
