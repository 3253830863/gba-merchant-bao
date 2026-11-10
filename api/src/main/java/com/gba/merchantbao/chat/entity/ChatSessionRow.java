package com.gba.merchantbao.chat.entity;

import java.time.LocalDateTime;

/** Database times are UTC, without implicit JVM timezone conversion. */
public record ChatSessionRow(long id, long merchantId, long ownerAccountId, String ownerRole,
                             String title, String status, LocalDateTime createdAt, LocalDateTime updatedAt,
                             LocalDateTime archivedAt, Long archivedBy, long nextMessageSeq,
                             Long activeRunId, LocalDateTime runDeadlineAt) {
    @Override public String toString() { return "ChatSessionRow[id=" + id + ", merchantId=" + merchantId + "]"; }
}
