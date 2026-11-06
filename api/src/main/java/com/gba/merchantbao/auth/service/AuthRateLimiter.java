package com.gba.merchantbao.auth.service;

import com.gba.merchantbao.auth.config.AuthLimits;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.util.AuthCrypto;
import com.gba.merchantbao.auth.cache.AuthRedis;
import com.gba.merchantbao.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuthRateLimiter {
    private final AuthRedis redis;
    private final AuthLimits limits;
    public AuthRateLimiter(AuthRedis redis, AuthLimits limits) { this.redis = redis; this.limits = limits; }
    public int smsCooldown() { return limits.getSmsCooldownSeconds(); }
    public void sms(long merchant, String phone, String remoteAddr) {
        String scope = merchant + ":LOGIN:" + AuthCrypto.hash(phone);
        check(redis.run("sms-reserve", List.of(redis.key("sms-cooldown:" + scope),
                redis.key("sms-quota:" + scope), redis.key("sms-ip:" + AuthCrypto.hash(remoteAddr))),
                "" + limits.getSmsCooldownSeconds(), "" + limits.getSmsPhonePerHour(), "" + limits.getSmsIpPerHour()));
    }
    public void login(String remoteAddr) { quota("login-ip:" + AuthCrypto.hash(remoteAddr), limits.getLoginIpPerWindow()); }
    public void refresh(String remoteAddr) { quota("refresh-ip:" + AuthCrypto.hash(remoteAddr), limits.getRefreshIpPerWindow()); }
    private void quota(String key, int max) {
        check(redis.run("quota", List.of(redis.key(key)), "" + max, "" + limits.getLoginWindowSeconds()));
    }
    public String passwordKey(long merchant, Long accountId, String identifier) {
        return redis.key("password-fail:" + merchant + ":" + (accountId != null ? "account:" + accountId : AuthCrypto.hash(identifier)));
    }
    public void passwordAllowed(String key) {
        check(redis.run("password-limit", List.of(key), "CHECK", "" + limits.getPasswordFailures(), "" + limits.getPasswordWindowSeconds()));
    }
    public void passwordFailed(String key) {
        check(redis.run("password-limit", List.of(key), "FAIL", "" + limits.getPasswordFailures(), "" + limits.getPasswordWindowSeconds()));
    }
    public void passwordSucceeded(String key) { redis.run("password-limit", List.of(key), "CLEAR", "0", "0"); }
    private void check(String value) {
        long retry = Long.parseLong(value);
        if (retry > 0) throw new AuthError(429, CommonErrorCode.AUTH_RATE_LIMITED, retry);
    }
}
