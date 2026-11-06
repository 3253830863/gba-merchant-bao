package com.gba.merchantbao.auth.application;

/** Replaceable transport. Implementations must not log recipient, code or body. */
@FunctionalInterface
public interface SmsSender {
    void sendLoginCode(String normalizedPhone, String code);
}
