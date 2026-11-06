package com.gba.merchantbao.auth.domain;

public final class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException() {
        super("Invalid access token");
    }
}
