package com.swyp.backend.terms.entity;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_terms_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "terms_document_id", nullable = false)
	private TermsDocument termsDocument;

	@Column(name = "agreed_at", nullable = false)
	private Instant agreedAt;

	public UserTermsAgreement(User user, TermsDocument termsDocument, Instant agreedAt) {
		this.user = user;
		this.termsDocument = termsDocument;
		this.agreedAt = agreedAt;
	}
}
