package com.gba.merchantbao.chat.vo;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.chat.entity.ChatMessageRow;
import java.time.*;
public record ChatMessageView(ApiId messageId,long messageSeq,ApiId runId,String clientMessageId,String senderType,String content,
                              String status,String errorCode,Long inputTokens,Long outputTokens,Instant createdAt,Instant completedAt) {
    public static ChatMessageView from(ChatMessageRow r){return new ChatMessageView(new ApiId(r.id()),r.messageSeq(),new ApiId(r.runId()),r.clientMessageId(),r.senderType(),r.content(),r.status(),r.errorCode(),r.inputTokens(),r.outputTokens(),utc(r.createdAt()),utc(r.completedAt()));}
    private static Instant utc(LocalDateTime t){return t==null?null:t.toInstant(ZoneOffset.UTC);}
}
