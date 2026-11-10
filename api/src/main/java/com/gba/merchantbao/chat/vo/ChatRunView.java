package com.gba.merchantbao.chat.vo;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.chat.service.PreparedRun;
public record ChatRunView(ApiId sessionId,ApiId runId,ApiId userMessageId,ApiId messageId,String status) {
    public static ChatRunView from(PreparedRun r){return new ChatRunView(new ApiId(r.sessionId()),new ApiId(r.runId()),new ApiId(r.userMessageId()),new ApiId(r.messageId()),r.status());}
}
