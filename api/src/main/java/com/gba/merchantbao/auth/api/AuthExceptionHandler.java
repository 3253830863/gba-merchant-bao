package com.gba.merchantbao.auth.api;

import com.gba.merchantbao.auth.domain.*;
import com.gba.merchantbao.common.error.CommonErrorCode;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestControllerAdvice(basePackages="com.gba.merchantbao.auth.api")
public class AuthExceptionHandler {
    private final AuthHttpErrors errors;
    public AuthExceptionHandler(AuthHttpErrors errors){this.errors=errors;}
    @ExceptionHandler(Exception.class)
    public void failure(Exception e,HttpServletRequest request,HttpServletResponse response) throws IOException {
        AuthError result;
        if(e instanceof AuthError a)result=a;
        else if(e instanceof InvalidAccessTokenException)result=AuthError.unauthorized();
        else if(e instanceof IllegalArgumentException||e instanceof org.springframework.http.converter.HttpMessageNotReadableException||e instanceof org.springframework.web.bind.MethodArgumentNotValidException||e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException)result=new AuthError(400,CommonErrorCode.INVALID_ARGUMENT);
        else if(e instanceof org.springframework.dao.DataAccessException)result=AuthError.dependency();
        else result=new AuthError(500,CommonErrorCode.INTERNAL_ERROR);
        errors.write(request,response,result);
    }
}
