package com.gba.merchantbao.auth.service;

/** Replaceable transport. Implementations must not log recipient, code or body. */
@FunctionalInterface
public interface SmsSender {
    void sendLoginCode(String normalizedPhone, String code);
}
