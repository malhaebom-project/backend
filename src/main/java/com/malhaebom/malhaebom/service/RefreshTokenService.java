package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.repository.RefreshTokenRepository;
import com.malhaebom.malhaebom.security.TokenHashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashing tokenHashing;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            TokenHashing tokenHashing
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHashing = tokenHashing;
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository
                .findByTokenHash(tokenHashing.sha256(rawToken))
                .ifPresent(token -> token.revoke());
    }
}
