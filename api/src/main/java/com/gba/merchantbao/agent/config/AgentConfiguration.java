package com.gba.merchantbao.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.agent.service.OpenAiAgentModel;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {
    @Bean AgentPolicy agentPolicy(AgentProperties properties) {
        properties.validate();
        return new AgentPolicy(properties.isEnabled());
    }
    public record AgentPolicy(boolean enabled) { }
    @Bean @ConditionalOnMissingBean(AgentModel.class)
    AgentModel agentModel(AgentPolicy policy,AgentProperties properties) {
        if(policy.enabled())return new OpenAiAgentModel(properties);
        return (messages, listener) -> {
            throw new IllegalStateException("Agent disabled");
        };
    }
}
