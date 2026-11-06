package com.gba.merchantbao.auth.domain;

import com.gba.merchantbao.common.error.CommonErrorCode;

/** Stable public failures; never attach credentials or storage exception text. */
public final class AuthError extends RuntimeException {
    private final int status;
    private final CommonErrorCode code;
    private final long retryAfter;

    public AuthError(int status, CommonErrorCode code) { this(status, code, 0); }
    public AuthError(int status, CommonErrorCode code, long retryAfter) {
        super(code.getMessage()); this.status = status; this.code = code; this.retryAfter = retryAfter;
    }
    public int status() { return status; }
    public CommonErrorCode code() { return code; }
    public long retryAfter() { return retryAfter; }
    public static AuthError unauthorized() { return new AuthError(401, CommonErrorCode.UNAUTHORIZED); }
    public static AuthError credentials() { return new AuthError(401, CommonErrorCode.AUTH_CREDENTIALS_INVALID); }
    public static AuthError dependency() { return new AuthError(503, CommonErrorCode.AUTH_DEPENDENCY_UNAVAILABLE); }
    public static AuthError smsUnavailable() { return new AuthError(503, CommonErrorCode.AUTH_SMS_UNAVAILABLE); }
}
