package com.gba.merchantbao.chat.mapper;

import com.gba.merchantbao.chat.entity.ChatOwner;
import com.gba.merchantbao.chat.entity.ChatSessionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper {
    /** Caller must construct the row from a verified actor, not client identity fields. */
    int insert(ChatSessionRow row);
    ChatSessionRow findOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId);
    /** Requires a short caller-managed transaction. */
    ChatSessionRow lockOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId);
    List<ChatSessionRow> pageOwned(@Param("owner") ChatOwner owner, @Param("beforeTime") LocalDateTime beforeTime,
                                   @Param("beforeId") Long beforeId, @Param("size") int size);
    int archiveOwned(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                     @Param("now") LocalDateTime now);
    /** Must share a transaction with both message inserts; expectedNextSeq comes from lockOwned. */
    int acquireRun(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                   @Param("expectedNextSeq") long expectedNextSeq, @Param("runId") long runId,
                   @Param("deadline") LocalDateTime deadline, @Param("now") LocalDateTime now);
    int releaseRun(@Param("owner") ChatOwner owner, @Param("sessionId") long sessionId,
                   @Param("runId") long runId, @Param("now") LocalDateTime now);
}
