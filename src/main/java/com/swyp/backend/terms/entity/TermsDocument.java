package com.swyp.backend.terms.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "terms_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsType type;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false, length = 100)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TermsRequirement requirement;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "effective_date")
	private LocalDate effectiveDate;

	public TermsDocument(
			UserRole role,
			TermsType type,
			int version,
			String title,
			TermsRequirement requirement,
			String content,
			LocalDate effectiveDate) {
		this.role = role;
		this.type = type;
		this.version = version;
		this.title = title;
		this.requirement = requirement;
		this.content = content;
		this.effectiveDate = effectiveDate;
	}
}
