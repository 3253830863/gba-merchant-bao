package com.gba.merchantbao.chat.vo;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.chat.entity.ChatSessionRow;
import java.time.*;
public record ChatSessionView(ApiId sessionId,ApiId merchantId,String title,String status,Instant createdAt,Instant updatedAt,
                              Instant archivedAt,ApiId activeRunId,String activeRunStatus) {
    public static ChatSessionView from(ChatSessionRow r){return from(r,null);}
    public static ChatSessionView from(ChatSessionRow r,String activeRunStatus){return new ChatSessionView(new ApiId(r.id()),new ApiId(r.merchantId()),r.title(),r.status(),utc(r.createdAt()),utc(r.updatedAt()),utc(r.archivedAt()),r.activeRunId()==null?null:new ApiId(r.activeRunId()),activeRunStatus);}
    private static Instant utc(LocalDateTime t){return t==null?null:t.toInstant(ZoneOffset.UTC);}
}
