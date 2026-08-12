package com.swyp.backend.admin.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Admin identity, role, and login credential. {@code password} stores a BCrypt hash (encoding is done
 * in the auth service, never here); admins authenticate with email + password (no social login).
 *
 * <p>Soft delete: {@code delete(..)} sets {@code deleted_at} instead of removing the row
 * ({@code @SQLDelete}), and {@code @SQLRestriction} hides deleted rows from all queries.
 */
@Getter
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

	/** BCrypt hash — never a plaintext password. */
	@Column(nullable = false, length = 255)
	private String password;

	private Instant deletedAt;

	public Admin(String email, String name, AdminType type, String password) {
		this.email = email;
		this.name = name;
		this.type = type;
		this.password = password;
	}
}
