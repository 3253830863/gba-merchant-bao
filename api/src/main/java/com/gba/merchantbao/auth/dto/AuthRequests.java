package com.gba.merchantbao.auth.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

/** Unknown members are rejected locally; legacy Jackson contracts are unchanged. */
public final class AuthRequests {
    private AuthRequests() { }
    public interface Strict {
        @JsonAnySetter default void reject(String name,Object value) {throw new IllegalArgumentException("Unknown auth property");}
    }
    public record Sms(@NotBlank String phone) implements Strict { @Override public String toString(){return "Sms[REDACTED]";} }
    public record ConsumerLogin(@NotBlank String phone,@Pattern(regexp="[0-9]{6}") @NotNull String code) implements Strict { @Override public String toString(){return "ConsumerLogin[REDACTED]";} }
    public record StaffLogin(@NotBlank String merchantCode,@NotBlank String identifier,@NotBlank String password) implements Strict { @Override public String toString(){return "StaffLogin[REDACTED]";} }
    public record StaffCreate(@NotBlank String username,@NotBlank String phone,@NotBlank @Size(max=64) String displayName,@NotBlank String password) implements Strict { @Override public String toString(){return "StaffCreate[REDACTED]";} }
    public record Refresh(@NotBlank String refreshToken,@NotBlank String refreshRequestId) implements Strict { @Override public String toString(){return "Refresh[REDACTED]";} }
    public record Logout(String refreshToken) implements Strict { @Override public String toString(){return "Logout[REDACTED]";} }
}
