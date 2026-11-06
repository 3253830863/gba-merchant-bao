package com.gba.merchantbao.legacy.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;

public class JwtUtil {
    /**
     * 生成jwt
     * 使用显式 HS256 算法，secretKey 为 Base64 编码的强对称密钥
     *
     * @param secretKey jwt秘钥
     * @param ttlMillis jwt过期时间(毫秒)
     * @param claims    设置的信息
     * @return
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 生成JWT的时间
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        // 设置jwt的body
        return Jwts.builder()
                .claims(claims)
                .signWith(signingKey(secretKey), Jwts.SIG.HS256)
                .expiration(exp)
                .compact();
    }

    /**
     * 验签并读取旧协议 claims（JWT payload 本身不是加密数据）
     *
     * @param secretKey jwt秘钥 此秘钥一定要保留好在服务端, 不能暴露出去, 否则sign就可以被伪造, 如果对接多个客户端建议改造成多个
     * @param token     加密后的token
     * @return
     */
    public static Claims parseJWT(String secretKey, String token) {
        var parser = Jwts.parser();
        var algorithms = parser.sig();
        Jwts.SIG.get().values().stream().filter(algorithm -> !"HS256".equals(algorithm.getId()))
                .forEach(algorithms::remove);
        Claims claims = parser
                .verifyWith(signingKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // Legacy tokens have exactly one old identity claim and no v1 protocol identity.
        if (claims.getExpiration() == null || claims.containsKey("iss") || claims.containsKey("aud")
                || claims.containsKey("sub") || claims.containsKey("merchantId") || claims.containsKey("role")
                || claims.containsKey("sid") || claims.containsKey("jti")
                || claims.containsKey("empId") == claims.containsKey("userId")) {
            throw new io.jsonwebtoken.MalformedJwtException("Invalid legacy token");
        }
        Object identity = claims.get(claims.containsKey("empId") ? "empId" : "userId");
        if (identity == null || !identity.toString().matches("[1-9][0-9]{0,18}")) {
            throw new io.jsonwebtoken.MalformedJwtException("Invalid legacy identity");
        }
        return claims;
    }

    private static SecretKey signingKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) throw new IllegalArgumentException("JWT Base64 key is required");
        byte[] raw = Base64.getDecoder().decode(secretKey);
        if (raw.length < 32) throw new IllegalArgumentException("JWT Base64 key must decode to at least 32 bytes");
        return Keys.hmacShaKeyFor(raw);
    }

}
