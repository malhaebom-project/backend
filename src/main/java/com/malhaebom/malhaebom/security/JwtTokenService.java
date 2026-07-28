package com.malhaebom.malhaebom.security;

import com.malhaebom.malhaebom.config.JwtProperties;
import com.malhaebom.malhaebom.domain.RefreshToken;
import com.malhaebom.malhaebom.repository.RefreshTokenRepository;
import com.malhaebom.malhaebom.service.AuthTokenIssuer;
import com.malhaebom.malhaebom.service.model.IssuedTokens;
import com.malhaebom.malhaebom.service.model.SessionSubject;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JwtTokenService implements AuthTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHashing tokenHashing;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtProperties properties,
            RefreshTokenRepository refreshTokenRepository,
            TokenHashing tokenHashing
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHashing = tokenHashing;
    }

    @Override
    @Transactional
    public IssuedTokens issue(SessionSubject subject) {
        Instant now = Instant.now();
        String accessToken = encode(
                subject,
                "access",
                now,
                now.plusSeconds(properties.accessTokenSeconds())
        );
        String refreshToken = encode(
                subject,
                "refresh",
                now,
                now.plusSeconds(properties.refreshTokenSeconds())
        );

        refreshTokenRepository.save(new RefreshToken(
                subject.accountId(),
                tokenHashing.sha256(refreshToken),
                now.plusSeconds(properties.refreshTokenSeconds())
        ));

        return new IssuedTokens(
                accessToken,
                refreshToken,
                properties.accessTokenSeconds(),
                properties.refreshTokenSeconds()
        );
    }

    private String encode(
            SessionSubject subject,
            String tokenType,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject.accountId().toString())
                .claim("role", subject.role().name())
                .claim("token_type", tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
