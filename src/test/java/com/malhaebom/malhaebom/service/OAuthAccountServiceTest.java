package com.malhaebom.malhaebom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malhaebom.malhaebom.domain.Account;
import com.malhaebom.malhaebom.domain.OAuthIdentity;
import com.malhaebom.malhaebom.domain.OAuthProvider;
import com.malhaebom.malhaebom.exception.AuthErrorCode;
import com.malhaebom.malhaebom.exception.AuthException;
import com.malhaebom.malhaebom.repository.AccountRepository;
import com.malhaebom.malhaebom.repository.OAuthIdentityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    OAuthIdentityRepository identityRepository;

    @Mock
    OidcUser oidcUser;

    OAuthAccountService service;

    @BeforeEach
    void setUp() {
        service = new OAuthAccountService(
                accountRepository,
                identityRepository
        );
    }

    @Test
    void 신규_Google_사용자는_OAuth_전용_계정으로_생성한다() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn(" Parent@Example.com ");
        when(oidcUser.getFullName()).thenReturn("보호자");
        when(identityRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase(
                "parent@example.com"
        )).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(identityRepository.save(any(OAuthIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account account = service.resolveOrCreate(
                OAuthProvider.GOOGLE,
                oidcUser
        );

        assertThat(account.getEmail()).isEqualTo("parent@example.com");
        assertThat(account.hasPassword()).isFalse();
        verify(identityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void 기존_이메일_계정은_자동으로_Google과_연결하지_않는다() {
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("parent@example.com");
        when(identityRepository.findByProviderAndProviderUserId(
                OAuthProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase(
                "parent@example.com"
        )).thenReturn(Optional.of(Account.createEmailGuardian(
                "parent@example.com",
                "encoded-password",
                "보호자"
        )));

        assertThatThrownBy(() -> service.resolveOrCreate(
                OAuthProvider.GOOGLE,
                oidcUser
        ))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> assertThat(
                        ((AuthException) exception).getErrorCode()
                ).isEqualTo(AuthErrorCode.ACCOUNT_LINK_REQUIRED));
    }
}
