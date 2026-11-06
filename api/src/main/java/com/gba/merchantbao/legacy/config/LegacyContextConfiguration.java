package com.gba.merchantbao.legacy.config;

import com.gba.merchantbao.legacy.context.BaseContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Configuration
public class LegacyContextConfiguration {
    @Bean public FilterRegistrationBean<OncePerRequestFilter> legacyContextCleanup() {
        var registration=new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
                BaseContext.removeCurrentId();try{chain.doFilter(r,s);}finally{BaseContext.removeCurrentId();}
            }
        });
        registration.addUrlPatterns("/admin/*","/user/*");registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+1);return registration;
    }
}
