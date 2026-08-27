package com.swyp.backend.store.entity;

import com.swyp.backend.common.BaseTimeEntity;
import com.swyp.backend.user.entity.User;
import jakarta.persistence.Column;
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
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "stores", uniqueConstraints = @UniqueConstraint(columnNames = "owner_user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id", nullable = false)
	private User owner;

	@Column(nullable = false, length = 100)
	private String name;

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

	public Store(
			User owner,
			String name,
			String address,
			String addressDetail,
			String phone,
			BigDecimal latitude,
			BigDecimal longitude,
			LocalTime businessOpenTime,
			LocalTime businessCloseTime) {
		this.owner = owner;
		this.name = name;
		this.address = address;
		this.addressDetail = addressDetail;
		this.phone = phone;
		this.latitude = latitude;
		this.longitude = longitude;
		this.businessOpenTime = businessOpenTime;
		this.businessCloseTime = businessCloseTime;
		this.status = StoreStatus.PENDING;
	}

	public void updateProfile(String name, String address, String addressDetail, String phone) {
		this.name = name;
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

	public void approve() {
		this.status = StoreStatus.APPROVED;
	}

	public void reject() {
		this.status = StoreStatus.REJECTED;
	}
}
