package com.gba.merchantbao.auth.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public final class AuthJwtKeyMaterial {
    private final SecretKey merchantStaffKey;
    private final SecretKey consumerKey;

    private AuthJwtKeyMaterial(AuthJwtProperties properties) {
        byte[] staff = decode("merchant.auth.jwt.merchant-staff-secret-base64", properties.merchantStaffSecretBase64());
        byte[] consumer = null;
        try {
            consumer = decode("merchant.auth.jwt.consumer-secret-base64", properties.consumerSecretBase64());
            if (MessageDigest.isEqual(staff, consumer)) {
                throw new IllegalStateException("merchant.auth.jwt.merchant-staff-secret-base64 and "
                        + "merchant.auth.jwt.consumer-secret-base64 must be different");
            }
            this.merchantStaffKey = Keys.hmacShaKeyFor(staff);
            this.consumerKey = Keys.hmacShaKeyFor(consumer);
        } finally {
            Arrays.fill(staff, (byte) 0);
            if (consumer != null) Arrays.fill(consumer, (byte) 0);
        }
    }

    public static AuthJwtKeyMaterial validate(AuthJwtProperties properties) {
        if (!"merchantbao".equals(properties.issuer()) || !"api-v1".equals(properties.audience())) {
            throw new IllegalStateException("merchant.auth.jwt issuer/audience must be merchantbao/api-v1");
        }
        return new AuthJwtKeyMaterial(properties);
    }

    public SecretKey merchantStaffKey() { return merchantStaffKey; }
    public SecretKey consumerKey() { return consumerKey; }

    static byte[] decode(String property, String value) {
        if (value == null || value.isBlank() || value.contains("REPLACE") || value.contains("CHANGE_ME")
                || value.contains("<") || value.contains(">")) {
            throw new IllegalStateException(property + " must contain a strong Base64 secret");
        }
        try {
            byte[] decoded = Decoders.BASE64.decode(value);
            if (decoded.length < 32) {
                Arrays.fill(decoded, (byte) 0);
                throw new IllegalStateException(property + " must decode to at least 32 bytes");
            }
            if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
                Arrays.fill(decoded, (byte) 0);
                throw new IllegalStateException(property + " must use canonical Base64 encoding");
            }
            return decoded;
        } catch (IllegalStateException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalStateException(property + " must contain valid Base64 encoding");
        }
    }
}
