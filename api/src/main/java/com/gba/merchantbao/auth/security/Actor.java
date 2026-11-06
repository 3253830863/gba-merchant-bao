package com.gba.merchantbao.auth.security;

/** Identity established by signature, server session and current database state. */
public record Actor(AuthPrincipal principal, String sessionId) { }
