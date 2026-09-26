package com.swyp.backend.user.dto;

import com.swyp.backend.hold.dto.CancelCreditBalance;
import com.swyp.backend.hold.dto.OwnerHoldStatus;
import com.swyp.backend.hold.entity.HoldCancelCreditEvent;
import com.swyp.backend.hold.entity.HoldCancelCreditReason;
import com.swyp.backend.notification.entity.DevicePlatform;
import com.swyp.backend.notification.entity.Notification;
import com.swyp.backend.notification.entity.NotificationPushState;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.entity.UserDeviceToken;
import com.swyp.backend.store.entity.Store;
import com.swyp.backend.store.entity.StoreStatus;
import com.swyp.backend.terms.entity.TermsType;
import com.swyp.backend.terms.entity.UserTermsAgreement;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.entity.UserLocation;
import com.swyp.backend.user.entity.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AdminUserDetailResponse(
		Long id,
		UserRole role,
		String nickname,
		@Nullable String phone,
		@Nullable String oauthProvider,
		@Nullable String oauthProviderId,
		boolean marketingOptIn,
		Instant termsAgreedAt,
		Instant createdAt,
		@Nullable Location location,
		@Nullable OwnedStore store,
		CancelCredits cancelCredits,
		HoldCounts holds,
		Notifications notifications,
		List<DeviceToken> deviceTokens,
		List<TermsAgreement> termsAgreements) {

	public record Location(String regionName, BigDecimal latitude, BigDecimal longitude, Instant updatedAt) {
		static Location from(UserLocation location) {
			return new Location(
					location.getRegionName(),
					location.getLatitude(),
					location.getLongitude(),
					location.getUpdatedAt());
		}
	}

	public record OwnedStore(Long id, String name, StoreStatus status, Instant createdAt) {
		static OwnedStore from(Store store) {
			return new OwnedStore(store.getId(), store.getName(), store.getStatus(), store.getCreatedAt());
		}
	}

	public record CancelCredits(
			int credits, int max, @Nullable Instant nextRefillAt, List<CancelCreditEvent> events) {
		static CancelCredits of(CancelCreditBalance balance, List<HoldCancelCreditEvent> events) {
			return new CancelCredits(
					balance.credits(),
					balance.max(),
					balance.nextRefillAt(),
					events.stream().map(CancelCreditEvent::from).toList());
		}
	}

	public record CancelCreditEvent(
			Long id, HoldCancelCreditReason reason, int delta, @Nullable Long holdId, Instant createdAt) {
		static CancelCreditEvent from(HoldCancelCreditEvent event) {
			return new CancelCreditEvent(
					event.getId(),
					event.getReason(),
					event.getDelta(),
					event.getHold() == null ? null : event.getHold().getId(),
					event.getCreatedAt());
		}
	}

	public record HoldCounts(
			long holding, long completed, long expired, long canceledByUser, long canceledByOwner) {
		static HoldCounts from(Map<OwnerHoldStatus, Long> counts) {
			return new HoldCounts(
					counts.getOrDefault(OwnerHoldStatus.HOLDING, 0L),
					counts.getOrDefault(OwnerHoldStatus.COMPLETED, 0L),
					counts.getOrDefault(OwnerHoldStatus.EXPIRED, 0L),
					counts.getOrDefault(OwnerHoldStatus.CANCELED_BY_USER, 0L),
					counts.getOrDefault(OwnerHoldStatus.CANCELED_BY_OWNER, 0L));
		}
	}

	public record Notifications(long unreadCount, List<RecentNotification> recent) {}

	public record RecentNotification(
			Long id,
			NotificationType type,
			String title,
			String body,
			@Nullable Instant readAt,
			NotificationPushState pushState,
			int pushAttempts,
			@Nullable Instant pushedAt,
			Instant createdAt) {
		static RecentNotification from(Notification notification) {
			return new RecentNotification(
					notification.getId(),
					notification.getType(),
					notification.getTitle(),
					notification.getBody(),
					notification.getReadAt(),
					notification.getPushState(),
					notification.getPushAttempts(),
					notification.getPushedAt(),
					notification.getCreatedAt());
		}
	}

	public record DeviceToken(
			Long id, DevicePlatform platform, String tokenSuffix, Instant lastUsedAt, Instant createdAt) {
		static DeviceToken from(UserDeviceToken token) {
			String fcmToken = token.getFcmToken();
			return new DeviceToken(
					token.getId(),
					token.getPlatform(),
					fcmToken.length() <= 8 ? fcmToken : "…" + fcmToken.substring(fcmToken.length() - 8),
					token.getLastUsedAt(),
					token.getCreatedAt());
		}
	}

	public record TermsAgreement(TermsType type, int version, String title, Instant agreedAt) {
		static TermsAgreement from(UserTermsAgreement agreement) {
			return new TermsAgreement(
					agreement.getTermsDocument().getType(),
					agreement.getTermsDocument().getVersion(),
					agreement.getTermsDocument().getTitle(),
					agreement.getAgreedAt());
		}
	}

	public static AdminUserDetailResponse of(
			User user,
			@Nullable UserLocation location,
			@Nullable Store store,
			CancelCreditBalance balance,
			List<HoldCancelCreditEvent> creditEvents,
			Map<OwnerHoldStatus, Long> holdCounts,
			long unreadNotifications,
			List<Notification> recentNotifications,
			List<UserDeviceToken> deviceTokens,
			List<UserTermsAgreement> termsAgreements) {
		return new AdminUserDetailResponse(
				user.getId(),
				user.getRole(),
				user.getNickname(),
				user.getPhone(),
				user.getOauthProvider(),
				user.getOauthProviderId(),
				user.isMarketingOptIn(),
				user.getTermsAgreedAt(),
				user.getCreatedAt(),
				location == null ? null : Location.from(location),
				store == null ? null : OwnedStore.from(store),
				CancelCredits.of(balance, creditEvents),
				HoldCounts.from(holdCounts),
				new Notifications(
						unreadNotifications,
						recentNotifications.stream().map(RecentNotification::from).toList()),
				deviceTokens.stream().map(DeviceToken::from).toList(),
				termsAgreements.stream().map(TermsAgreement::from).toList());
	}
}
