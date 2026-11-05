package com.gba.merchantbao.common.api;

import com.fasterxml.jackson.annotation.JsonValue;

/** Use at the API boundary to avoid losing BIGINT precision in JavaScript. */
public record ApiId(long value) {
    @JsonValue
    public String asString() {
        return Long.toString(value);
    }
}
