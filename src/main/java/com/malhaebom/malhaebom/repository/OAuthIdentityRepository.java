package com.malhaebom.malhaebom.repository;

import com.malhaebom.malhaebom.domain.OAuthIdentity;
import com.malhaebom.malhaebom.domain.OAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository
        extends JpaRepository<OAuthIdentity, UUID> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    );
}
