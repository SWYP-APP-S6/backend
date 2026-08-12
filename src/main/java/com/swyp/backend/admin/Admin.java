package com.swyp.backend.admin;

import com.swyp.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Admin identity and role record. Authentication (how an admin proves who they are) is out of scope
 * for now — this holds identity + role only.
 *
 * <p>Soft delete: {@code delete(..)} sets {@code deleted_at} instead of removing the row
 * ({@code @SQLDelete}), and {@code @SQLRestriction} hides deleted rows from all queries.
 */
@Entity
@Table(name = "admins")
@SQLDelete(sql = "update admins set deleted_at = now() where id = ?")
@SQLRestriction("deleted_at is null")
public class Admin extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AdminType type;

	private Instant deletedAt;

	protected Admin() {
	}

	public Admin(String email, String name, AdminType type) {
		this.email = email;
		this.name = name;
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public AdminType getType() {
		return type;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
