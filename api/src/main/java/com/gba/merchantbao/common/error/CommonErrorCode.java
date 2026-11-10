package com.gba.merchantbao.common.error;

import lombok.Getter;

@Getter
public enum CommonErrorCode {
    INVALID_ARGUMENT(10001, "invalid argument"),
    UNAUTHORIZED(10002, "unauthorized"),
    FORBIDDEN(10003, "forbidden"),
    RESOURCE_NOT_FOUND(10004, "resource not found"),
    CONFLICT(10009, "conflict"),
    INTERNAL_ERROR(10500, "internal server error"),
    AUTH_CREDENTIALS_INVALID(11001, "invalid credentials"),
    AUTH_SMS_CODE_INVALID(11002, "invalid SMS code"),
    AUTH_RATE_LIMITED(11003, "rate limited"),
    AUTH_DEPENDENCY_UNAVAILABLE(11501, "authentication dependency unavailable"),
    AUTH_SMS_UNAVAILABLE(11502, "SMS unavailable"),
    AGENT_CONTEXT_TOO_LARGE(13001, "AGENT_CONTEXT_TOO_LARGE"),
    CHAT_SESSION_BUSY(13002, "chat session busy"),
    CHAT_IDEMPOTENCY_CONFLICT(13003, "chat idempotency conflict"),
    CHAT_SESSION_ARCHIVED(13004, "chat session archived"),
    AGENT_DISABLED(13501, "agent disabled"),
    DEPENDENCY_ERROR(13502, "model dependency unavailable"),
    TIMEOUT(13503, "model response timeout"),
    CHAT_DEPENDENCY_UNAVAILABLE(13504, "chat storage unavailable");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
