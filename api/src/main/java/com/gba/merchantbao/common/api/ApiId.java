package com.gba.merchantbao.common.api;

import com.fasterxml.jackson.annotation.JsonValue;

/** Use at the API boundary to avoid losing BIGINT precision in JavaScript. */
public record ApiId(long value) {
    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    public static ApiId parse(String value) { return new ApiId(Long.parseLong(value)); }
    @JsonValue
    public String asString() {
        return Long.toString(value);
    }
}
