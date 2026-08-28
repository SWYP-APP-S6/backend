package com.swyp.backend.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.admin.entity.Admin;
import com.swyp.backend.admin.entity.AdminType;
import com.swyp.backend.admin.repository.AdminRepository;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
class AdminStoreControllerTest {

	private static final String EMAIL = "store-admin@swyp.test";
	private static final String PASSWORD = "store-admin-1234";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	private Long pendingStoreId;

	@BeforeEach
	void setUp() {
		if (adminRepository.findByEmail(EMAIL).isEmpty()) {
			adminRepository.save(
				new Admin(EMAIL, "Store Admin", AdminType.SUPER, passwordEncoder.encode(PASSWORD)));
		}
		storeRepository.deleteAll();
		userRepository.deleteAll();

		User owner = userRepository.save(
			new User(UserRole.OWNER, "심사대기점주", "01012345678", false, Instant.now()));
		pendingStoreId = storeRepository.save(newStore(owner, "심사대기 가게")).getId();

		User approvedOwner = userRepository.save(
			new User(UserRole.OWNER, "승인된점주", "01087654321", false, Instant.now()));
		Store approved = storeRepository.save(newStore(approvedOwner, "승인된 가게"));
		approved.approve();
		storeRepository.save(approved);
	}

	private Store newStore(User owner, String name) {
		return new Store(
			owner, name, "서울특별시 강남구 역삼로 1", null, "0212341234",
			new BigDecimal("37.500600"), new BigDecimal("127.036500"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
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

	private static String statusBody(String status) {
		return """
			{"status":"%s"}""".formatted(status);
	}

	@Test
	void stores_withoutToken_areNotReadable() throws Exception {
		mockMvc.perform(get("/admin/stores")).andExpect(status().isUnauthorized());
	}

	@Test
	void stores_filteredByStatus_returnOnlyThatStatus() throws Exception {
		String token = accessToken();

		mockMvc.perform(get("/admin/stores").param("status", "PENDING")
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.content[0].name").value("심사대기 가게"))
			.andExpect(jsonPath("$.data.content[0].owner.nickname").value("심사대기점주"));

		mockMvc.perform(get("/admin/stores").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void approving_movesTheStoreOutOfPending() throws Exception {
		mockMvc.perform(patch("/admin/stores/{id}/status", pendingStoreId)
				.header("Authorization", "Bearer " + accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("APPROVED")))
			.andExpect(status().isOk());

		assertThat(storeRepository.findById(pendingStoreId))
			.get()
			.extracting(Store::getStatus)
			.isEqualTo(StoreStatus.APPROVED);
	}

	@Test
	void rejecting_marksTheStoreRejected() throws Exception {
		mockMvc.perform(patch("/admin/stores/{id}/status", pendingStoreId)
				.header("Authorization", "Bearer " + accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("REJECTED")))
			.andExpect(status().isOk());

		assertThat(storeRepository.findById(pendingStoreId))
			.get()
			.extracting(Store::getStatus)
			.isEqualTo(StoreStatus.REJECTED);
	}

	@Test
	void revertingToPending_isRejected() throws Exception {
		mockMvc.perform(patch("/admin/stores/{id}/status", pendingStoreId)
				.header("Authorization", "Bearer " + accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("PENDING")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CANNOT_REVERT_TO_PENDING"));
	}

	@Test
	void updatingAnUnknownStore_returnsNotFound() throws Exception {
		mockMvc.perform(patch("/admin/stores/{id}/status", 999999)
				.header("Authorization", "Bearer " + accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(statusBody("APPROVED")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
	}
}
