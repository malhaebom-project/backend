package com.malhaebom.malhaebom.domain.repository;

import com.malhaebom.malhaebom.domain.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
	Optional<LoginSession> findByRefreshToken(String refreshToken);

	void deleteByUserId(Long userId);
}
