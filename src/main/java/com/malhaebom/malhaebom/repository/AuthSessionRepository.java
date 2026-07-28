package com.malhaebom.malhaebom.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.AuthSession;

/**
 * 인증 세션을 영속화하고 Refresh Token 해시로 조회한다.
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	/**
	 * Refresh Token의 SHA-256 해시와 일치하는 세션을 조회한다.
	 *
	 * <p>폐기 및 만료 여부는 호출자가 일관된 기준 시각으로 판정할 수 있도록
	 * 이 쿼리에서 필터링하지 않는다.</p>
	 *
	 * @param refreshTokenHash 조회할 Refresh Token 해시
	 * @return 일치하는 인증 세션
	 */
	Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}
