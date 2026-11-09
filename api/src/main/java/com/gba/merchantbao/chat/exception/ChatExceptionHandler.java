package com.gba.merchantbao.chat.exception;

import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages="com.gba.merchantbao.chat.controller")
public class ChatExceptionHandler {
    @ExceptionHandler(ChatError.class)
    ResponseEntity<ApiResponse<Void>> chat(ChatError error,HttpServletRequest request){return body(error.getStatus(),error.getCode(),request);}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception error,HttpServletRequest request){return body(400,CommonErrorCode.INVALID_ARGUMENT,request);}
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> unsupported(Exception error,HttpServletRequest request){return body(415,CommonErrorCode.INVALID_ARGUMENT,request);}
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiResponse<Void>> unacceptable(Exception error,HttpServletRequest request){return body(406,CommonErrorCode.INVALID_ARGUMENT,request);}
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> storage(Exception error,HttpServletRequest request){return body(503,CommonErrorCode.CHAT_DEPENDENCY_UNAVAILABLE,request);}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error,HttpServletRequest request){return body(500,CommonErrorCode.INTERNAL_ERROR,request);}
    private ResponseEntity<ApiResponse<Void>> body(int status,CommonErrorCode code,HttpServletRequest request){
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(ApiResponse.failure(code,code.getMessage(),(String)request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
