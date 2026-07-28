package com.malhaebom.malhaebom.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 브라우저나 기기 한 대의 Refresh Token 기반 로그인 세션을 나타낸다.
 *
 * <p>Refresh Token 원문 대신 SHA-256 해시만 보관하며, 세션은 생성 시 정해진
 * 절대 만료 시각을 가진다. 토큰 회전은 같은 세션 행에서 해시만 교체한다.</p>
 */
@Entity
@Table(
	name = "auth_sessions",
	indexes = @Index(
		name = "idx_auth_sessions_account_revoked_expires",
		columnList = "account_id, revoked_at, expires_at"
	),
	uniqueConstraints = @UniqueConstraint(
		name = "uk_auth_sessions_refresh_token_hash",
		columnNames = "refresh_token_hash"
	)
)
public class AuthSession {

	private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

	@Id
	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "refresh_token_hash", nullable = false, length = 64)
	private String refreshTokenHash;

	@Column(
		name = "expires_at",
		nullable = false,
		columnDefinition = "timestamp with time zone"
	)
	private Instant expiresAt;

	@Column(name = "revoked_at", columnDefinition = "timestamp with time zone")
	private Instant revokedAt;

	@Column(
		name = "created_at",
		nullable = false,
		updatable = false,
		columnDefinition = "timestamp with time zone"
	)
	private Instant createdAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	/**
	 * JPA가 엔티티를 복원할 때 사용하는 생성자다.
	 */
	protected AuthSession() {
	}

	private AuthSession(
		UUID sessionId,
		UUID accountId,
		String refreshTokenHash,
		Instant expiresAt,
		Instant createdAt
	) {
		this.sessionId = requireNonNull(sessionId, "sessionId");
		this.accountId = requireNonNull(accountId, "accountId");
		this.refreshTokenHash = requireRefreshTokenHash(refreshTokenHash);
		this.expiresAt = requireNonNull(expiresAt, "expiresAt");
		this.createdAt = requireNonNull(createdAt, "createdAt");
	}

	/**
	 * 새 인증 세션을 생성한다.
	 *
	 * <p>만료 시각은 전달받은 현재 시각과 Refresh Session TTL로 한 번만 계산한다.
	 * 따라서 이후 Refresh Token을 회전해도 절대 만료 시각은 연장되지 않는다.</p>
	 *
	 * @param accountId 로그인한 내부 계정 ID
	 * @param refreshTokenHash Refresh Token의 SHA-256 lowercase hex 해시
	 * @param now 세션 생성 시각
	 * @param refreshSessionTtl 세션의 절대 유효기간
	 * @return 고유한 UUID가 부여된 새 인증 세션
	 * @throws NullPointerException 필수 인자가 {@code null}인 경우
	 * @throws IllegalArgumentException TTL이나 해시 형식이 올바르지 않은 경우
	 */
	public static AuthSession create(
		UUID accountId,
		String refreshTokenHash,
		Instant now,
		Duration refreshSessionTtl
	) {
		requireNonNull(accountId, "accountId");
		requireRefreshTokenHash(refreshTokenHash);
		requireNonNull(now, "now");
		requirePositive(refreshSessionTtl);

		return new AuthSession(
			UUID.randomUUID(),
			accountId,
			refreshTokenHash,
			now.plus(refreshSessionTtl),
			now
		);
	}

	/**
	 * 주어진 시각에 세션을 사용할 수 있는지 확인한다.
	 *
	 * @param now 활성 여부를 판정할 기준 시각
	 * @return 폐기되지 않았고 만료 시각 전이면 {@code true}
	 * @throws NullPointerException {@code now}가 {@code null}인 경우
	 */
	public boolean isActive(Instant now) {
		requireNonNull(now, "now");
		return revokedAt == null && expiresAt.isAfter(now);
	}

	/**
	 * 활성 세션의 Refresh Token 해시를 교체한다.
	 *
	 * <p>{@link Version} 필드가 동시에 수행되는 회전 요청의 충돌을 감지하며,
	 * 세션의 절대 만료 시각은 변경하지 않는다.</p>
	 *
	 * @param newRefreshTokenHash 새 Refresh Token의 SHA-256 lowercase hex 해시
	 * @param now 회전 시점 및 활성 여부 판정 기준 시각
	 * @throws NullPointerException 필수 인자가 {@code null}인 경우
	 * @throws IllegalArgumentException 해시 형식이 올바르지 않은 경우
	 * @throws IllegalStateException 세션이 이미 폐기되었거나 만료된 경우
	 */
	public void rotate(String newRefreshTokenHash, Instant now) {
		String validatedHash = requireRefreshTokenHash(newRefreshTokenHash);
		requireNonNull(now, "now");

		if (!isActive(now)) {
			throw new IllegalStateException(
				"Revoked or expired auth session cannot rotate its refresh token."
			);
		}

		refreshTokenHash = validatedHash;
	}

	/**
	 * 세션을 폐기한다.
	 *
	 * <p>여러 번 호출해도 최초 폐기 시각을 유지한다.</p>
	 *
	 * @param now 폐기 시각
	 * @throws NullPointerException {@code now}가 {@code null}인 경우
	 */
	public void revoke(Instant now) {
		requireNonNull(now, "now");
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public String getRefreshTokenHash() {
		return refreshTokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getVersion() {
		return version;
	}

	private static <T> T requireNonNull(T value, String fieldName) {
		return Objects.requireNonNull(value, fieldName + " must not be null.");
	}

	private static void requirePositive(Duration refreshSessionTtl) {
		requireNonNull(refreshSessionTtl, "refreshSessionTtl");
		if (refreshSessionTtl.isZero() || refreshSessionTtl.isNegative()) {
			throw new IllegalArgumentException("refreshSessionTtl must be positive.");
		}
	}

	private static String requireRefreshTokenHash(String refreshTokenHash) {
		requireNonNull(refreshTokenHash, "refreshTokenHash");
		if (!SHA_256_HEX_PATTERN.matcher(refreshTokenHash).matches()) {
			throw new IllegalArgumentException(
				"refreshTokenHash must be a 64-character lowercase SHA-256 hex value."
			);
		}
		return refreshTokenHash;
	}
}
