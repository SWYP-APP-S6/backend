package com.swyp.backend.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swyp.backend.AppDataCleaner;
import com.swyp.backend.RedisTestcontainersConfiguration;
import com.swyp.backend.TestcontainersConfiguration;
import com.swyp.backend.common.security.JwtTokenProvider;
import com.swyp.backend.common.security.TokenRealm;
import com.swyp.backend.product.PhotoFixture;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.repository.StoreRepository;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserRole;
import com.swyp.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class OwnerProductPhotoControllerTest {

	@Autowired
	AppDataCleaner appDataCleaner;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	StoreRepository storeRepository;

	@Autowired
	JwtTokenProvider tokenProvider;

	private String token;

	@BeforeEach
	void setUp() {
		appDataCleaner.clear();
		User owner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "테스트점주", null, false, Instant.now()));
		Store store = new Store(
			owner, "테스트가게", "04524", "서울특별시 강남구 역삼로 1", null, "0212345678",
			new BigDecimal("37.500000"), new BigDecimal("127.030000"),
			LocalTime.of(9, 0), LocalTime.of(21, 0));
		store.replaceBusinessDays(EnumSet.allOf(DayOfWeek.class));
		storeRepository.saveAndFlush(store);
		token = tokenProvider.createAccessToken(TokenRealm.USER, owner.getId(), UserRole.OWNER.name());
	}

	@AfterEach
	void tearDown() {
		appDataCleaner.clear();
	}

	@Test
	void anUploadedPhoto_isServedAtTheUrlItAnsweredWith() throws Exception {
		String photoUrl = PhotoFixture.uploadedPhotoUrl(mockMvc, token);

		assertThat(photoUrl).contains("/uploads/products/").endsWith(".png");
		mockMvc.perform(get(URI.create(photoUrl).getPath()))
			.andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
					.isEqualTo(PhotoFixture.onePixelPng()));
	}

	@Test
	void theServedPhoto_needsNoToken() throws Exception {
		String path = URI.create(PhotoFixture.uploadedPhotoUrl(mockMvc, token)).getPath();

		mockMvc.perform(get(path)).andExpect(status().isOk());
	}

	@Test
	void aFileThatIsNotAnImage_isRejected() throws Exception {
		MockMultipartFile pretender = new MockMultipartFile(
			"file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
			"<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/owner/products/photos")
				.file(pretender)
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
	}

	@Test
	void uploadingWithoutAStore_isRejected() throws Exception {
		User strayOwner = userRepository.saveAndFlush(
			new User(UserRole.OWNER, "가게없는점주", null, false, Instant.now()));
		String strayToken = tokenProvider.createAccessToken(
			TokenRealm.USER, strayOwner.getId(), UserRole.OWNER.name());

		mockMvc.perform(multipart("/owner/products/photos")
				.file(PhotoFixture.png("photo.png"))
				.header("Authorization", "Bearer " + strayToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("STORE_NOT_REGISTERED"));
	}

	@Test
	void registeringWithAPhotoThisServerNeverStored_isRejected() throws Exception {
		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,\
					"salePrice":800,"photoUrl":"https://picsum.photos/seed/carrot/400/300"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PHOTO_URL"));
	}

	@Test
	void registeringWithAPhotoUrlThatEscapesTheStore_isRejected() throws Exception {
		String photoUrl = PhotoFixture.uploadedPhotoUrl(mockMvc, token);
		String escaping = photoUrl.replace("/uploads/products/", "/uploads/../../etc/");

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,\
					"salePrice":800,"photoUrl":"%s"}""".formatted(escaping)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PHOTO_URL"));
	}

	@Test
	void aPhotoThisServerStored_getsThroughRegistration() throws Exception {
		String photoUrl = PhotoFixture.uploadedPhotoUrl(mockMvc, token);

		mockMvc.perform(post("/owner/products")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"당근","category":"VEGETABLE","initialQty":10,"originalPrice":1000,\
					"salePrice":800,"photoUrl":"%s"}""".formatted(photoUrl)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.photoUrl").value(photoUrl));
	}
}
