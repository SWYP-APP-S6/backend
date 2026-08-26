package com.swyp.backend.recipe.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.User;
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

@Getter
@Entity
@Table(
		name = "recipe_feedback",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "recipe_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeFeedback extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipe_id", nullable = false)
	private Recipe recipe;

	@Column(nullable = false)
	private boolean helpful;

	public RecipeFeedback(User user, Recipe recipe, boolean helpful) {
		this.user = user;
		this.recipe = recipe;
		this.helpful = helpful;
	}

	public void changeHelpful(boolean helpful) {
		this.helpful = helpful;
	}
}
