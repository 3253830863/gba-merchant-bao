package com.gba.merchantbao.auth.exception;

public final class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException() {
        super("Invalid access token");
    }
}
