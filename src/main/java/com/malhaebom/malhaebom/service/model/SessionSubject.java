package com.malhaebom.malhaebom.service.model;

import com.malhaebom.malhaebom.domain.AccountRole;

import java.util.Objects;
import java.util.UUID;

/**
 * 계정 담당자 A가 토큰 담당자 B에게 전달하는 최소 계정 정보다.
 */
public record SessionSubject(
        UUID accountId,
        AccountRole role
) {

    public SessionSubject {
        Objects.requireNonNull(accountId, "accountId는 필수입니다.");
        Objects.requireNonNull(role, "role은 필수입니다.");
    }
}
