package com.gba.merchantbao.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchant.auth.jwt")
public record AuthJwtProperties(String merchantStaffSecretBase64, String consumerSecretBase64,
                                String issuer, String audience) {
    @Override
    public String toString() { return "AuthJwtProperties[secrets=REDACTED]"; }
}
