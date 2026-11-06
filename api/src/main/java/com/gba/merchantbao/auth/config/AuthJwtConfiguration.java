package com.gba.merchantbao.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthJwtProperties.class)
public class AuthJwtConfiguration {
    @Bean
    AuthJwtKeyMaterial authJwtKeyMaterial(AuthJwtProperties properties) {
        return AuthJwtKeyMaterial.validate(properties);
    }
}
