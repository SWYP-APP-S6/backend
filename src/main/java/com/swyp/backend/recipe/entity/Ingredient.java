package com.swyp.backend.recipe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ingredient dictionary — the search/matching backbone ("냉장고 재료로 검색"). {@code normKey} collapses
 * spelling/phrasing variants (다진 마늘 / 마늘 2쪽 / 통마늘) onto one canonical entry.
 */
@Getter
@Entity
@Table(name = "ingredients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 64)
	private String name;

	@Column(name = "norm_key", nullable = false, length = 64)
	private String normKey;

	@Column(length = 32)
	private String category;

	public Ingredient(String name, String normKey, String category) {
		this.name = name;
		this.normKey = normKey;
		this.category = category;
	}
}
