package com.gba.merchantbao.chat.mapper;

import com.gba.merchantbao.chat.entity.ChatMessageRow;
import com.gba.merchantbao.chat.entity.ChatOwner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** All access joins the owned session, including asynchronous callback updates. */
@Mapper
public interface ChatMessageMapper {
    /** Insert in the same short transaction as acquireRun; zero means ownership/run/sequence mismatch. */
    int insertOwned(@Param("owner") ChatOwner owner, @Param("message") ChatMessageRow message);
    ChatMessageRow findOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                             @Param("messageId") long messageId);
    ChatMessageRow findUserByClientId(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                                      @Param("clientMessageId") String clientMessageId);
    List<ChatMessageRow> pageOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                                   @Param("afterSeq") long afterSeq, @Param("size") int size);
    /** Caller must finish and releaseRun atomically; a zero result must not produce a success event. */
    int finishOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                    @Param("messageId") long messageId, @Param("runId") long runId,
                    @Param("status") String status, @Param("content") String content,
                    @Param("errorCode") String errorCode, @Param("inputTokens") Long inputTokens,
                    @Param("outputTokens") Long outputTokens, @Param("now") LocalDateTime now);
}
