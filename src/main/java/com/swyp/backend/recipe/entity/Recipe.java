package com.swyp.backend.recipe.entity;

import com.swyp.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "recipes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String source;

	@Column(name = "source_id", nullable = false, length = 64)
	private String sourceId;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(length = 32)
	private String category;

	@Column(name = "cook_method", length = 32)
	private String cookMethod;

	@Column(name = "cook_time_minutes")
	private Short cookTimeMinutes;

	@Column(nullable = false)
	private short servings;

	@Column(name = "image_url", length = 512)
	private String imageUrl;

	@Column(name = "image_thumb_url", length = 512)
	private String imageThumbUrl;

	@Column(name = "origin_image_url", length = 512)
	private String originImageUrl;

	@Column(name = "source_url", length = 512)
	private String sourceUrl;

	@Column(nullable = false, length = 20)
	private String license;

	@Column(name = "is_published", nullable = false)
	private boolean published;

	@Column(name = "view_count", nullable = false)
	private int viewCount;

	@Column(name = "like_count", nullable = false)
	private int likeCount;

	public Recipe(
			String source,
			String sourceId,
			String title,
			String category,
			String cookMethod,
			short servings,
			String license) {
		this.source = source;
		this.sourceId = sourceId;
		this.title = title;
		this.category = category;
		this.cookMethod = cookMethod;
		this.servings = servings;
		this.license = license;
	}

	public void assignCookTimeMinutes(Short cookTimeMinutes) {
		this.cookTimeMinutes = cookTimeMinutes;
	}

	public void assignImages(String imageUrl, String imageThumbUrl, String originImageUrl) {
		this.imageUrl = imageUrl;
		this.imageThumbUrl = imageThumbUrl;
		this.originImageUrl = originImageUrl;
	}

	public void publish() {
		this.published = true;
	}

	public void unpublish() {
		this.published = false;
	}

	public void increaseViewCount() {
		this.viewCount++;
	}

	public void increaseLikeCount() {
		this.likeCount++;
	}

	public void decreaseLikeCount() {
		if (this.likeCount > 0) {
			this.likeCount--;
		}
	}
}
