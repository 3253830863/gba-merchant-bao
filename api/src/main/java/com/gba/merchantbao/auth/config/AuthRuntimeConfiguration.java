package com.gba.merchantbao.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthLimits.class)
public class AuthRuntimeConfiguration {
    @Bean Clock authClock() { return Clock.systemUTC(); }
}
