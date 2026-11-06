package com.gba.merchantbao.auth.entity;

/** A resolved tenant locator, not proof of authorization. */
public record Tenant(long id, String merchantCode) {
}
