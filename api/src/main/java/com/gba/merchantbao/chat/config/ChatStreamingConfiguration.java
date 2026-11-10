package com.gba.merchantbao.chat.config;

import com.gba.merchantbao.agent.config.AgentProperties;
import org.springframework.context.annotation.*;
import java.util.concurrent.*;
import org.springframework.web.cors.*;

@Configuration(proxyBeanMethods=false)
public class ChatStreamingConfiguration {
    @Bean CorsConfigurationSource chatCorsConfigurationSource(AgentProperties p){
        var cors=new CorsConfiguration();cors.setAllowedOrigins(p.getAllowedOrigins());
        cors.setAllowedMethods(java.util.List.of("GET","POST","OPTIONS"));
        cors.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","X-Request-Id","Accept"));
        cors.setExposedHeaders(java.util.List.of("X-Request-Id","Location"));cors.setAllowCredentials(false);cors.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/v1/chat/**",cors);return source;
    }
    @Bean(destroyMethod="shutdownNow") ExecutorService chatSseWriters(AgentProperties p){
        return new ThreadPoolExecutor(p.getWorkers(),p.getWorkers(),0,TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(p.getQueueCapacity()),r->{var t=new Thread(r,"chat-sse-writer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean(destroyMethod="shutdownNow") ScheduledExecutorService chatSseHeartbeats(AgentProperties p){
        var x=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"chat-sse-heartbeat");t.setDaemon(true);return t;});
        x.setRemoveOnCancelPolicy(true);return x;
    }
}
