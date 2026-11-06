package com.gba.merchantbao.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Canonical identifiers at the auth boundary; passwords are deliberately never trimmed. */
public final class AuthInput {
    private AuthInput() { }

    public static String phone(String value) {
        String s = required(value).trim();
        if (s.startsWith("+86")) s = s.substring(3);
        if (!s.matches("1[3-9][0-9]{9}")) throw invalid();
        return "+86" + s;
    }

    public static String merchantCode(String value) {
        String s = required(value).trim().toUpperCase(Locale.ROOT);
        if (!s.matches("[A-Z][A-Z0-9_-]{0,63}")) throw invalid();
        return s;
    }

    public static String storeSlug(String value) { return merchantCode(value); }

    public static String username(String value) {
        String s = required(value).trim().toLowerCase(Locale.ROOT);
        if (!s.matches("[a-z][a-z0-9_-]{2,31}")) throw invalid();
        return s;
    }

    public static Identifier identifier(String value) {
        String s = required(value).trim();
        return s.startsWith("+") || (!s.isEmpty() && Character.isDigit(s.charAt(0)))
                ? new Identifier(phone(s), true) : new Identifier(username(s), false);
    }

    public static String password(String value) {
        String s = required(value);
        int length = s.codePointCount(0, s.length());
        if (length < 8 || length > 64 || s.getBytes(StandardCharsets.UTF_8).length > 72) throw invalid();
        // Reject unpaired surrogates, which otherwise have ambiguous UTF-8 encodings.
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(s)) throw invalid();
        return s;
    }

    private static String required(String s) { if (s == null) throw invalid(); return s; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid auth input"); }
    public record Identifier(String value, boolean phone) {
        @Override public String toString() { return "Identifier[REDACTED]"; }
    }
}
