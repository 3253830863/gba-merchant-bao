package com.gba.merchantbao.auth.config;

import com.gba.merchantbao.auth.application.SmsSender;
import com.gba.merchantbao.auth.domain.AuthError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthSmsProperties.class)
public class AuthSmsConfiguration {
    @Bean
    SmsPolicy authSmsPolicy(AuthSmsProperties properties, Environment env) {
        boolean dev = env.acceptsProfiles(Profiles.of("dev"));
        boolean nonprod = env.acceptsProfiles(Profiles.of("dev", "test"))
                && !env.acceptsProfiles(Profiles.of("prod"));
        if (!java.util.Set.of("disabled", "mock").contains(properties.provider())
                || (!nonprod && (properties.provider().equals("mock") || properties.exposeDebugCode()))
                || (properties.exposeDebugCode() && !properties.provider().equals("mock"))) {
            throw new IllegalStateException("Invalid merchant.auth.sms provider/debug configuration");
        }
        return new SmsPolicy(properties.provider().equals("mock"), dev && properties.exposeDebugCode());
    }

    @Bean @ConditionalOnMissingBean(SmsSender.class)
    SmsSender smsSender(SmsPolicy policy) {
        return policy.mock() ? (phone, code) -> { } : (phone, code) -> { throw AuthError.smsUnavailable(); };
    }

    public record SmsPolicy(boolean mock, boolean exposeDebugCode) { }
}
