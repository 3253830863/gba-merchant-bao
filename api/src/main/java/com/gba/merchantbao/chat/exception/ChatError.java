package com.gba.merchantbao.chat.exception;

import com.gba.merchantbao.common.error.CommonErrorCode;
import lombok.Getter;

@Getter
public final class ChatError extends RuntimeException {
    private final int status;
    private final CommonErrorCode code;
    public ChatError(int status,CommonErrorCode code){super(code.getMessage());this.status=status;this.code=code;}
    public static ChatError invalid(){return new ChatError(400,CommonErrorCode.INVALID_ARGUMENT);}
    public static ChatError missing(){return new ChatError(404,CommonErrorCode.RESOURCE_NOT_FOUND);}
    public static ChatError busy(){return new ChatError(409,CommonErrorCode.CHAT_SESSION_BUSY);}
    public static ChatError storage(){return new ChatError(503,CommonErrorCode.CHAT_DEPENDENCY_UNAVAILABLE);}
}
