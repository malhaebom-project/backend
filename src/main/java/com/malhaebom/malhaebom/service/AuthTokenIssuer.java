package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.service.model.IssuedTokens;
import com.malhaebom.malhaebom.service.model.SessionSubject;

/**
 * 계정 담당자 A와 토큰 담당자 B 사이의 최소 호출 계약이다.
 */
public interface AuthTokenIssuer {

    IssuedTokens issue(SessionSubject subject);
}
