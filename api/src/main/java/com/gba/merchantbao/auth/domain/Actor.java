package com.gba.merchantbao.auth.domain;

/** Identity established by signature, server session and current database state. */
public record Actor(AuthPrincipal principal, String sessionId) { }
