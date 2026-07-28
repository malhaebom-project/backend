package com.malhaebom.malhaebom.repository;

import com.malhaebom.malhaebom.domain.OAuthLoginCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthLoginCodeRepository
        extends JpaRepository<OAuthLoginCode, UUID> {

    Optional<OAuthLoginCode> findByCodeHash(String codeHash);
}
