package com.gba.merchantbao.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.auth.exception.AuthError;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class AuthHttpErrors {
    private final ObjectMapper json;
    public AuthHttpErrors(ObjectMapper json){this.json=json;}
    public void write(HttpServletRequest request,HttpServletResponse response,AuthError error) throws IOException {
        request.setAttribute("auth.errorCode",error.code().getCode());
        response.setStatus(error.status());response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        if(error.retryAfter()>0)response.setHeader("Retry-After",Long.toString(error.retryAfter()));
        json.writeValue(response.getOutputStream(),ApiResponse.failure(error.code(),error.getMessage(),(String)request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
