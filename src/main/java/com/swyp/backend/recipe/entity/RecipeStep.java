package com.swyp.backend.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One cooking step. {@code seq} is a contiguous 1-based renumbering done at load time. */
@Getter
@Entity
@Table(
		name = "recipe_steps",
		uniqueConstraints = @UniqueConstraint(columnNames = {"recipe_id", "seq"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@Column(nullable = false)
	private short seq;

	@Column(nullable = false)
	private String content;

	@Column(name = "image_url", length = 512)
	private String imageUrl;

	public RecipeStep(Recipe recipe, short seq, String content, String imageUrl) {
		this.recipe = recipe;
		this.seq = seq;
		this.content = content;
		this.imageUrl = imageUrl;
	}
}
