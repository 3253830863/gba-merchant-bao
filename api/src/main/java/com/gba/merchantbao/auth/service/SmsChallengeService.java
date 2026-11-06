package com.gba.merchantbao.auth.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gba.merchantbao.auth.config.AuthSmsConfiguration.SmsPolicy;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.util.AuthCrypto;
import com.gba.merchantbao.auth.cache.AuthRedis;
import com.gba.merchantbao.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;

@Service
public class SmsChallengeService {
    private final AuthRedis redis;
    private final SmsSender sender;
    private final SmsPolicy policy;
    private final AuthRateLimiter rates;
    private final Clock clock;
    public SmsChallengeService(AuthRedis redis, SmsSender sender, SmsPolicy policy, AuthRateLimiter rates, Clock clock) {
        this.redis = redis; this.sender = sender; this.policy = policy; this.rates = rates; this.clock = clock;
    }
    public SmsResult send(long merchant, String phone, String remoteAddr) {
        rates.sms(merchant, phone, remoteAddr);
        String key = key(merchant, phone), id = AuthCrypto.randomToken(), salt = AuthCrypto.randomToken(), code = AuthCrypto.code();
        redis.run("sms-challenge", List.of(key), "CREATE", id, salt, AuthCrypto.hash(salt + ":" + code),
                "" + clock.instant().plusSeconds(300).getEpochSecond());
        try { sender.sendLoginCode(phone, code); }
        catch (RuntimeException e) {
            redis.run("sms-challenge", List.of(key), "DELETE", id);
            throw AuthError.smsUnavailable();
        }
        if (!redis.run("sms-challenge", List.of(key), "ACTIVATE", id).equals("1")) throw AuthError.smsUnavailable();
        return new SmsResult(300, rates.smsCooldown(), policy.exposeDebugCode() ? code : null);
    }
    public void consume(long merchant, String phone, String code) {
        var challenge = redis.hash(key(merchant, phone));
        String salt = (String) challenge.get("salt"), id = (String) challenge.get("challengeId");
        if (salt == null || id == null || code == null || !code.matches("[0-9]{6}")) throw invalid();
        String result = redis.run("sms-challenge", List.of(key(merchant, phone)), "CONSUME", id,
                AuthCrypto.hash(salt + ":" + code), "" + clock.instant().getEpochSecond());
        if (!result.equals("1")) throw invalid();
    }
    private String key(long merchant, String phone) { return redis.key("sms:" + merchant + ":LOGIN:" + AuthCrypto.hash(phone)); }
    private AuthError invalid() { return new AuthError(400, CommonErrorCode.AUTH_SMS_CODE_INVALID); }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SmsResult(int expiresIn, int retryAfter, String debugCode) {
        @Override public String toString() { return "SmsResult[REDACTED]"; }
    }
}
