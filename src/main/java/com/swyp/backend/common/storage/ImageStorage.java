package com.swyp.backend.common.storage;

import com.swyp.backend.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class ImageStorage {

	private static final Map<String, String> EXTENSION_BY_FORMAT = Map.of(
			"jpeg", "jpg",
			"jpg", "jpg",
			"png", "png");

	private final Path root;
	private final String publicBaseUrl;
	private final Clock clock;

	public ImageStorage(ImageStorageProperties properties, Clock clock) {
		this.root = Path.of(properties.rootDir()).toAbsolutePath().normalize();
		this.publicBaseUrl = properties.publicBaseUrl();
		this.clock = clock;
	}

	public String store(MultipartFile file, String category) {
		String extension = extensionOf(file);
		LocalDate today = LocalDate.now(clock);
		String key = "%s/%d/%02d/%s.%s".formatted(
				category, today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
		Path destination = root.resolve(key);
		try {
			Files.createDirectories(destination.getParent());
			try (InputStream source = file.getInputStream()) {
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.error("Could not write an uploaded image to {}", destination, e);
			throw new BusinessException(StorageErrorCode.IMAGE_STORE_FAILED);
		}
		return publicBaseUrl + "/" + key;
	}

	public boolean holds(String url) {
		String prefix = publicBaseUrl + "/";
		if (url == null || !url.startsWith(prefix)) {
			return false;
		}
		Path file = root.resolve(url.substring(prefix.length())).normalize();
		return file.startsWith(root) && Files.isRegularFile(file);
	}

	private String extensionOf(MultipartFile file) {
		try (InputStream source = file.getInputStream();
				ImageInputStream image = ImageIO.createImageInputStream(source)) {
			if (image == null) {
				throw new BusinessException(StorageErrorCode.UNSUPPORTED_IMAGE);
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
			if (!readers.hasNext()) {
				throw new BusinessException(StorageErrorCode.UNSUPPORTED_IMAGE);
			}
			ImageReader reader = readers.next();
			try {
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				String extension = EXTENSION_BY_FORMAT.get(format);
				if (extension == null) {
					throw new BusinessException(StorageErrorCode.UNSUPPORTED_IMAGE);
				}
				return extension;
			} finally {
				reader.dispose();
			}
		} catch (IOException e) {
			throw new BusinessException(StorageErrorCode.UNSUPPORTED_IMAGE);
		}
	}
}
