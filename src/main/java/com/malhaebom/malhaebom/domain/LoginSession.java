package com.malhaebom.malhaebom.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "login_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 2048)
	private String accessToken;

	@Column(nullable = false, unique = true, length = 2048)
	private String refreshToken;

	@Column(nullable = false)
	private Instant accessTokenExpiresAt;

	@Column(nullable = false)
	private Instant refreshTokenExpiresAt;

	private LoginSession(
		User user,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt
	) {
		this.user = user;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.accessTokenExpiresAt = accessTokenExpiresAt;
		this.refreshTokenExpiresAt = refreshTokenExpiresAt;
	}

	public static LoginSession create(
		User user,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt
	) {
		return new LoginSession(
			user,
			accessToken,
			refreshToken,
			accessTokenExpiresAt,
			refreshTokenExpiresAt
		);
	}

	public void rotate(
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt
	) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.accessTokenExpiresAt = accessTokenExpiresAt;
		this.refreshTokenExpiresAt = refreshTokenExpiresAt;
	}
}
