package com.swyp.backend.user.entity;

import com.swyp.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLocation extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "region_code", length = 10)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 9, scale = 6)
	private BigDecimal longitude;

	public UserLocation(
			User user,
			String regionCode,
			String regionName,
			BigDecimal latitude,
			BigDecimal longitude) {
		this.user = user;
		this.regionCode = regionCode;
		this.regionName = regionName;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public void changeRegion(
			String regionCode, String regionName, BigDecimal latitude, BigDecimal longitude) {
		this.regionCode = regionCode;
		this.regionName = regionName;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
