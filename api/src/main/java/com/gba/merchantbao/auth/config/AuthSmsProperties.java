package com.gba.merchantbao.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("merchant.auth.sms")
public record AuthSmsProperties(String provider, boolean exposeDebugCode) {
    public AuthSmsProperties { if (provider == null) provider = "disabled"; }
}
