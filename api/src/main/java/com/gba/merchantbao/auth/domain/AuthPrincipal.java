package com.gba.merchantbao.auth.domain;

public record AuthPrincipal(long accountId, long merchantId, AccountRole role) {
    public AuthPrincipal {
        if (accountId <= 0 || merchantId <= 0 || role == null) {
            throw new IllegalArgumentException("Account ID, merchant ID and role are required");
        }
    }
}
