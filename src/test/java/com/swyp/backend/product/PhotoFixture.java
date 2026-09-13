package com.swyp.backend.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

public final class PhotoFixture {

	private PhotoFixture() {}

	public static byte[] onePixelPng() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	public static MockMultipartFile png(String filename) {
		return new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, onePixelPng());
	}

	public static String uploadedPhotoUrl(MockMvc mockMvc, String token) throws Exception {
		String body = mockMvc.perform(multipart("/owner/products/photos")
				.file(png("photo.png"))
				.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.photoUrl");
	}
}
