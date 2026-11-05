package com.gba.merchantbao.config;

import com.gba.merchantbao.common.id.IdGenerator;
import com.gba.merchantbao.common.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class IdConfiguration {
    @Bean
    public IdGenerator idGenerator(@Value("${merchant.id.worker-id}") int workerId) {
        return new SnowflakeIdGenerator(workerId, Clock.systemUTC());
    }
}
