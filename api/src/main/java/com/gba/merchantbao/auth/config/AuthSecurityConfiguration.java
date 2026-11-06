package com.gba.merchantbao.auth.config;

import com.gba.merchantbao.auth.service.AuthRateLimiter;
import com.gba.merchantbao.auth.service.SessionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.auth.handler.AuthHttpErrors;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.auth.security.AuthSecurityFilter;
import jakarta.validation.Validator;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class AuthSecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain v1(HttpSecurity http,SessionService sessions,AuthRateLimiter rates,ObjectMapper json,Validator validator,AuthHttpErrors errors)throws Exception {
        return http.securityMatcher("/api/v1/**").csrf(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new AuthSecurityFilter(sessions,rates,json,validator,errors),UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(c->c.requestMatchers(AuthSecurityFilter::anonymous).permitAll().anyRequest().authenticated())
            .exceptionHandling(c->c.authenticationEntryPoint((r,s,e)->errors.write(r,s,AuthError.unauthorized()))
                .accessDeniedHandler((r,s,e)->errors.write(r,s,new AuthError(403,com.gba.merchantbao.common.error.CommonErrorCode.FORBIDDEN))))
            .build();
    }
    @Bean @Order(2)
    SecurityFilterChain legacy(HttpSecurity http)throws Exception {
        return http.csrf(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.anyRequest().permitAll()).build();
    }
}
