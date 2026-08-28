package com.malhaebom.malhaebom.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malhaebom.malhaebom.domain.LoginSession;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
	Optional<LoginSession> findByRefreshToken(String refreshToken);

	void deleteByUserId(Long userId);
}
