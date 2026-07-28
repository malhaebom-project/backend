package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.domain.Account;
import com.malhaebom.malhaebom.domain.OAuthIdentity;
import com.malhaebom.malhaebom.domain.OAuthProvider;
import com.malhaebom.malhaebom.exception.AuthErrorCode;
import com.malhaebom.malhaebom.exception.AuthException;
import com.malhaebom.malhaebom.repository.AccountRepository;
import com.malhaebom.malhaebom.repository.OAuthIdentityRepository;
import java.util.Locale;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

    private final AccountRepository accountRepository;
    private final OAuthIdentityRepository identityRepository;

    public OAuthAccountService(
            AccountRepository accountRepository,
            OAuthIdentityRepository identityRepository
    ) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public Account resolveOrCreate(
            OAuthProvider provider,
            OidcUser user
    ) {
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AuthException(AuthErrorCode.OAUTH_EMAIL_NOT_VERIFIED);
        }

        return identityRepository
                .findByProviderAndProviderUserId(provider, user.getSubject())
                .map(identity -> existingAccount(identity))
                .orElseGet(() -> createAccount(provider, user));
    }

    private Account existingAccount(OAuthIdentity identity) {
        Account account = identity.getAccount();
        if (!account.canLogin()) {
            throw new AuthException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        identity.markLogin();
        return account;
    }

    private Account createAccount(
            OAuthProvider provider,
            OidcUser user
    ) {
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new AuthException(AuthErrorCode.ACCOUNT_LINK_REQUIRED);
        }

        String displayName = user.getFullName();
        if (displayName == null || displayName.isBlank()) {
            displayName = email.substring(0, email.indexOf('@'));
        }

        Account account = accountRepository.save(
                Account.createOAuthGuardian(email, displayName)
        );
        identityRepository.save(new OAuthIdentity(
                account,
                provider,
                user.getSubject()
        ));
        return account;
    }
}
