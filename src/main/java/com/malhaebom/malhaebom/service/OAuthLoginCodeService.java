package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.config.OAuthProperties;
import com.malhaebom.malhaebom.domain.Account;
import com.malhaebom.malhaebom.domain.OAuthLoginCode;
import com.malhaebom.malhaebom.exception.AuthErrorCode;
import com.malhaebom.malhaebom.exception.AuthException;
import com.malhaebom.malhaebom.repository.AccountRepository;
import com.malhaebom.malhaebom.repository.OAuthLoginCodeRepository;
import com.malhaebom.malhaebom.security.TokenHashing;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthLoginCodeService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final OAuthLoginCodeRepository loginCodeRepository;
    private final AccountRepository accountRepository;
    private final OAuthProperties properties;
    private final TokenHashing tokenHashing;

    public OAuthLoginCodeService(
            OAuthLoginCodeRepository loginCodeRepository,
            AccountRepository accountRepository,
            OAuthProperties properties,
            TokenHashing tokenHashing
    ) {
        this.loginCodeRepository = loginCodeRepository;
        this.accountRepository = accountRepository;
        this.properties = properties;
        this.tokenHashing = tokenHashing;
    }

    @Transactional
    public String issue(UUID accountId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawCode = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        loginCodeRepository.save(new OAuthLoginCode(
                accountId,
                tokenHashing.sha256(rawCode),
                Instant.now().plusSeconds(properties.loginCodeSeconds())
        ));
        return rawCode;
    }

    @Transactional
    public Account exchange(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new AuthException(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID);
        }

        OAuthLoginCode loginCode = loginCodeRepository
                .findByCodeHash(tokenHashing.sha256(rawCode))
                .orElseThrow(() ->
                        new AuthException(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID)
                );

        UUID accountId;
        try {
            accountId = loginCode.consume(Instant.now());
        } catch (IllegalStateException exception) {
            throw new AuthException(AuthErrorCode.OAUTH_LOGIN_CODE_INVALID);
        }

        return accountRepository.findById(accountId)
                .filter(Account::canLogin)
                .orElseThrow(() ->
                        new AuthException(AuthErrorCode.ACCOUNT_NOT_ACTIVE)
                );
    }
}
