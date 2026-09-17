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
