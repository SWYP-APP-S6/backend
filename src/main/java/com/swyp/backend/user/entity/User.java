package com.swyp.backend.user.entity;

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

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(name = "oauth_provider", length = 20)
	private String oauthProvider;

	@Column(name = "oauth_provider_id", length = 100)
	private String oauthProviderId;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(length = 20)
	private String phone;

	@Column(name = "marketing_opt_in", nullable = false)
	private boolean marketingOptIn;

	@Column(name = "terms_agreed_at", nullable = false)
	private Instant termsAgreedAt;

	@Column(name = "tester_allowed", nullable = false)
	private boolean testerAllowed;

	@Column(name = "test_mode", nullable = false)
	private boolean testMode;

	@Column(nullable = false, updatable = false)
	private boolean tester;

	public User(
			UserRole role,
			String nickname,
			String phone,
			boolean marketingOptIn,
			Instant termsAgreedAt) {
		this.role = role;
		this.nickname = nickname;
		this.phone = phone;
		this.marketingOptIn = marketingOptIn;
		this.termsAgreedAt = termsAgreedAt;
	}

	public static User testAccount(
			UserRole role,
			String nickname,
			String phone,
			boolean marketingOptIn,
			Instant termsAgreedAt) {
		User user = new User(role, nickname, phone, marketingOptIn, termsAgreedAt);
		user.tester = true;
		return user;
	}

	public User newTestAccount(Instant termsAgreedAt) {
		User testAccount = testAccount(role, nickname, phone, marketingOptIn, termsAgreedAt);
		testAccount.linkOauthAccount(oauthProvider, oauthProviderId);
		return testAccount;
	}

	public void linkOauthAccount(String oauthProvider, String oauthProviderId) {
		this.oauthProvider = oauthProvider;
		this.oauthProviderId = oauthProviderId;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void changePhone(String phone) {
		this.phone = phone;
	}

	public void changeMarketingOptIn(boolean marketingOptIn) {
		this.marketingOptIn = marketingOptIn;
	}

	public void allowTesting() {
		this.testerAllowed = true;
	}

	public void revokeTesting() {
		this.testerAllowed = false;
		this.testMode = false;
	}

	public void enterTestMode() {
		this.testMode = true;
	}

	public void leaveTestMode() {
		this.testMode = false;
	}
}
