package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.Account;
import java.util.UUID;

public record AccountResponse(
        UUID accountId,
        String email,
        String name,
        String role
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getRole().name()
        );
    }
}
