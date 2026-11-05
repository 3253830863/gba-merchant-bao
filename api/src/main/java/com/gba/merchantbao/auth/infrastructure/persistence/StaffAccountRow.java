package com.gba.merchantbao.auth.infrastructure.persistence;

import java.time.Instant;

/** Not an API DTO. Never include phone or password hash in toString/logs. */
public record StaffAccountRow(long id, long merchantId, String username, String phone, String displayName,
                              String passwordHash, String status, Instant createdAt, Instant updatedAt,
                              Long createdBy, int version) {
    @Override
    public String toString() {
        return "StaffAccountRow[id=" + id + ", merchantId=" + merchantId + "]";
    }
}
