package com.gba.merchantbao.auth.entity;

import java.time.Instant;

/** Persistence only. API summaries must explicitly mask the phone and stringify IDs. */
public record ConsumerAccountRow(long id, long merchantId, String phone, String displayName, String status,
                                 Instant createdAt, Instant updatedAt, int version) {
    @Override
    public String toString() {
        return "ConsumerAccountRow[id=" + id + ", merchantId=" + merchantId + "]";
    }
}
