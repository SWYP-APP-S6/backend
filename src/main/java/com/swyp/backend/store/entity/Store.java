package com.swyp.backend.store.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stores", uniqueConstraints = @UniqueConstraint(columnNames = "owner_user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

	private static final int MAX_CATEGORIES = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id", nullable = false)
	private User owner;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "postal_code", length = 10)
	private String postalCode;

	@Column(nullable = false, length = 255)
	private String address;

	@Column(name = "address_detail", length = 255)
	private String addressDetail;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude;

	@Column(name = "business_open_time", nullable = false)
	private LocalTime businessOpenTime;

	@Column(name = "business_close_time", nullable = false)
	private LocalTime businessCloseTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StoreStatus status;

	@Column(name = "business_registration_number", length = 20)
	private String businessRegistrationNumber;

	@Column(name = "application_note", columnDefinition = "text")
	private String applicationNote;

	@ElementCollection
	@CollectionTable(
			name = "store_categories",
			joinColumns = @JoinColumn(name = "store_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20)
	private Set<StoreCategory> categories = new LinkedHashSet<>();

	@ElementCollection
	@CollectionTable(
			name = "store_business_days",
			joinColumns = @JoinColumn(name = "store_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private Set<DayOfWeek> businessDays = new LinkedHashSet<>();

	public Store(
			User owner,
			String name,
			String postalCode,
			String address,
			String addressDetail,
			String phone,
			BigDecimal latitude,
			BigDecimal longitude,
			LocalTime businessOpenTime,
			LocalTime businessCloseTime) {
		this.owner = owner;
		this.name = name;
		this.postalCode = postalCode;
		this.address = address;
		this.addressDetail = addressDetail;
		this.phone = phone;
		this.latitude = latitude;
		this.longitude = longitude;
		this.businessOpenTime = businessOpenTime;
		this.businessCloseTime = businessCloseTime;
		this.status = StoreStatus.PENDING;
	}

	public Set<StoreCategory> getCategories() {
		return Collections.unmodifiableSet(categories);
	}

	public Set<DayOfWeek> getBusinessDays() {
		return Collections.unmodifiableSet(businessDays);
	}

	public void replaceCategories(Collection<StoreCategory> categories) {
		if (categories.isEmpty() || categories.size() > MAX_CATEGORIES) {
			throw new IllegalArgumentException(
					"store categories must be between 1 and " + MAX_CATEGORIES);
		}
		this.categories.clear();
		this.categories.addAll(categories);
	}

	public boolean opensOn(DayOfWeek day) {
		return businessDays.contains(day);
	}

	public boolean isOpenAt(ZonedDateTime at) {
		if (!opensOn(at.getDayOfWeek())) {
			return false;
		}
		LocalTime time = at.toLocalTime();
		return !time.isBefore(businessOpenTime) && time.isBefore(businessCloseTime);
	}

	public void replaceBusinessDays(Collection<DayOfWeek> businessDays) {
		if (businessDays.isEmpty()) {
			throw new IllegalArgumentException("store must open on at least one day");
		}
		this.businessDays.clear();
		this.businessDays.addAll(businessDays);
	}

	public void updateProfile(
			String name, String postalCode, String address, String addressDetail, String phone) {
		this.name = name;
		this.postalCode = postalCode;
		this.address = address;
		this.addressDetail = addressDetail;
		this.phone = phone;
	}

	public void updateLocation(BigDecimal latitude, BigDecimal longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public void updateBusinessHours(LocalTime businessOpenTime, LocalTime businessCloseTime) {
		this.businessOpenTime = businessOpenTime;
		this.businessCloseTime = businessCloseTime;
	}

	public void submitApplication(String businessRegistrationNumber, String applicationNote) {
		this.businessRegistrationNumber = businessRegistrationNumber;
		this.applicationNote = applicationNote;
	}

	public void approve() {
		this.status = StoreStatus.APPROVED;
	}

	public void reject() {
		this.status = StoreStatus.REJECTED;
	}
}
