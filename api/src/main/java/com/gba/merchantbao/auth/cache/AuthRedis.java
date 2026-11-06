package com.gba.merchantbao.auth.cache;

import com.gba.merchantbao.auth.exception.AuthError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Auth uses string serialization; the legacy RedisTemplate remains independent. */
@Component
public class AuthRedis {
    private final StringRedisTemplate redis;
    private final String prefix;
    private final Map<String, DefaultRedisScript<String>> scripts = new ConcurrentHashMap<>();
    public AuthRedis(StringRedisTemplate redis, @Value("${merchant.auth.namespace:${spring.profiles.active:default}}") String env) {
        if (!env.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalStateException("Invalid merchant.auth.namespace");
        this.redis = redis; this.prefix = "mba:" + env + ":auth:";
    }
    public String key(String suffix) { return prefix + suffix; }
    public String run(String name, List<String> keys, String... args) {
        try {
            String result = redis.execute(scripts.computeIfAbsent(name, n -> {
                var s = new DefaultRedisScript<String>(); s.setLocation(new ClassPathResource("auth/" + n + ".lua"));
                s.setResultType(String.class); return s;
            }), keys, (Object[]) args);
            if (result == null) throw AuthError.dependency();
            return result;
        } catch (org.springframework.dao.DataAccessException e) { throw AuthError.dependency(); }
    }
    public Map<Object, Object> hash(String key) {
        try { return redis.opsForHash().entries(key); }
        catch (org.springframework.dao.DataAccessException e) { throw AuthError.dependency(); }
    }
}
