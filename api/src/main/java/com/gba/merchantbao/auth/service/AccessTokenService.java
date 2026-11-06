package com.gba.merchantbao.auth.service;

import com.gba.merchantbao.auth.config.AuthJwtKeyMaterial;
import com.gba.merchantbao.auth.config.AuthJwtProperties;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.AuthPrincipal;
import com.gba.merchantbao.auth.exception.InvalidAccessTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public final class AccessTokenService {
    public static final long ACCESS_TTL_SECONDS = 7200;
    private static final String CLAIM_MERCHANT_ID = "merchantId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_SESSION_ID = "sid";
    private final AuthJwtKeyMaterial keys;
    private final AuthJwtProperties properties;

    public AccessTokenService(AuthJwtKeyMaterial keys, AuthJwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    public IssuedAccessToken issue(AuthPrincipal principal, String sessionId, Instant now, Instant originalAbsoluteAt) {
        if (principal == null || sessionId == null || sessionId.isBlank() || now == null || originalAbsoluteAt == null
                || !originalAbsoluteAt.isAfter(now)) {
            throw new IllegalArgumentException("Session ID and a future original absolute expiry are required");
        }
        SecretKey key = keyFor(principal.role());
        now = now.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = min(now.plusSeconds(ACCESS_TTL_SECONDS), originalAbsoluteAt).truncatedTo(ChronoUnit.SECONDS);
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("No full second remains before absolute expiry");
        String jti;
        do {
            jti = UUID.randomUUID().toString();
        } while (jti.equals(sessionId));
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .subject(Long.toString(principal.accountId()))
                .claim(CLAIM_MERCHANT_ID, Long.toString(principal.merchantId()))
                .claim(CLAIM_ROLE, principal.role().name())
                .claim(CLAIM_SESSION_ID, sessionId)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, jti, now, expiresAt);
    }

    /** Verifies the signed protocol identity; callers must still check session and account state before authorization. */
    public VerifiedAccessToken verify(String token, Instant now) {
        if (token == null || token.isBlank() || now == null) throw new InvalidAccessTokenException();
        Claims staffClaims = parse(token, keys.merchantStaffKey(), now);
        if (staffClaims != null) return principal(staffClaims, AccountRole.MERCHANT_STAFF, now);
        Claims consumerClaims = parse(token, keys.consumerKey(), now);
        if (consumerClaims != null) return principal(consumerClaims, AccountRole.CONSUMER, now);
        throw new InvalidAccessTokenException();
    }

    private Claims parse(String token, SecretKey key, Instant now) {
        try {
            var parser = Jwts.parser();
            // JJWT 0.12.6 rejects an empty registry during clear(), before add() can run.
            var algorithms = parser.sig();
            Jwts.SIG.get().values().stream().filter(algorithm -> !"HS256".equals(algorithm.getId()))
                    .forEach(algorithms::remove);
            return parser
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .requireAudience(properties.audience())
                    .clock(() -> Date.from(now))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private VerifiedAccessToken principal(Claims claims, AccountRole expectedRole, Instant now) {
        try {
            String subject = claims.getSubject();
            String merchantId = claims.get(CLAIM_MERCHANT_ID, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            String sid = claims.get(CLAIM_SESSION_ID, String.class);
            String jti = claims.getId();
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (subject == null || merchantId == null || role == null || sid == null || sid.isBlank()
                    || jti == null || jti.isBlank() || issuedAt == null || expiresAt == null
                    || issuedAt.toInstant().isAfter(now) || !expiresAt.toInstant().isAfter(now)
                    || !expiresAt.after(issuedAt)
                    || expiresAt.toInstant().isAfter(issuedAt.toInstant().plusSeconds(ACCESS_TTL_SECONDS))
                    || !subject.matches("[1-9][0-9]{0,18}") || !merchantId.matches("[1-9][0-9]{0,18}")
                    || claims.containsKey("empId") || claims.containsKey("userId")
                    || !claims.getAudience().equals(java.util.Set.of(properties.audience()))) {
                throw new IllegalArgumentException();
            }
            AccountRole parsedRole = AccountRole.valueOf(role);
            if (parsedRole != expectedRole || sid.equals(jti)) throw new IllegalArgumentException();
            return new VerifiedAccessToken(new AuthPrincipal(Long.parseLong(subject),
                    Long.parseLong(merchantId), parsedRole), sid, jti, issuedAt.toInstant(), expiresAt.toInstant());
        } catch (RuntimeException ignored) {
            throw new InvalidAccessTokenException();
        }
    }

    private SecretKey keyFor(AccountRole role) {
        return switch (role) {
            case MERCHANT_STAFF -> keys.merchantStaffKey();
            case CONSUMER -> keys.consumerKey();
        };
    }

    private static Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }

    public record IssuedAccessToken(String token, String tokenId, Instant issuedAt, Instant expiresAt) {
        @Override public String toString() { return "IssuedAccessToken[token=REDACTED]"; }
    }
    public record VerifiedAccessToken(AuthPrincipal principal, String sessionId, String tokenId,
                                      Instant issuedAt, Instant expiresAt) { }
}
