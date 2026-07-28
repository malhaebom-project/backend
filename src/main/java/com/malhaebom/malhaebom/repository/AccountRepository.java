package com.malhaebom.malhaebom.repository;

import com.malhaebom.malhaebom.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmailIgnoreCase(String email);
}
