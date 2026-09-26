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
import com.swyp.backend.analytics.entity.DomainEvent;
import com.swyp.backend.analytics.entity.DomainEventType;
import com.swyp.backend.analytics.repository.DomainEventRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
class AdminDomainEventControllerTest {

	private static final String EMAIL = "event-admin@swyp.test";
	private static final String PASSWORD = "event-admin-1234";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	DomainEventRepository domainEventRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	Clock clock;

	private Long consumerId;
	private Long ownerId;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
					new Admin(EMAIL, "Event Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		consumerId = userRepository.save(
				new User(UserRole.CONSUMER, "소비자", null, false, Instant.now())).getId();
		ownerId = userRepository.save(
				new User(UserRole.OWNER, "점주", null, false, Instant.now())).getId();

		domainEventRepository.save(DomainEvent.builder()
				.eventType(DomainEventType.HOLD_CREATE)
				.userId(consumerId)
				.payload(Map.of("qty", 2, "productName", "복숭아 4입"))
				.build());
		domainEventRepository.save(DomainEvent.builder()
				.eventType(DomainEventType.HOLD_FAIL)
				.userId(consumerId)
				.payload(Map.of("code", "INSUFFICIENT_QTY"))
				.build());
		domainEventRepository.save(DomainEvent.builder()
				.eventType(DomainEventType.STOCK_ADJUST)
				.userId(ownerId)
				.payload(Map.of("stockBefore", 5, "stockAfter", 1))
				.build());
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
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
	void events_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/events")).andExpect(status().isUnauthorized());
	}

	@Test
	void events_comeNewestFirst_withTheirPayload() throws Exception {
		mockMvc.perform(get("/admin/events").header("Authorization", "Bearer " + accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.content[0].eventType").value("STOCK_ADJUST"))
				.andExpect(jsonPath("$.data.content[0].userId").value(ownerId))
				.andExpect(jsonPath("$.data.content[0].payload.stockAfter").value(1))
				.andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.data.content[2].eventType").value("HOLD_CREATE"))
				.andExpect(jsonPath("$.data.content[2].payload.productName").value("복숭아 4입"));
	}

	@Test
	void events_canBeNarrowedByTypeAndUser() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/events").param("type", "HOLD_FAIL")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].payload.code").value("INSUFFICIENT_QTY"));

		mockMvc.perform(get("/admin/events").param("userId", String.valueOf(consumerId))
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void events_canBeNarrowedToADateRange_inSeoulDays() throws Exception {
		String token = accessToken();
		LocalDate today = LocalDate.now(clock);

		mockMvc.perform(get("/admin/events")
						.param("from", today.toString()).param("to", today.toString())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3));

		mockMvc.perform(get("/admin/events").param("to", today.minusDays(1).toString())
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}
}
