package com.gba.merchantbao.chat.entity;

/** Server-resolved identity only; never bind this record from a chat request body. */
public record ChatOwner(long merchantId, long accountId, String role) { }
