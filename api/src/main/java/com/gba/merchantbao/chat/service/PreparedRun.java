package com.gba.merchantbao.chat.service;
import com.gba.merchantbao.chat.entity.ChatOwner;
import dev.langchain4j.data.message.ChatMessage;
import java.time.Instant;
import java.util.List;
/** Committed acceptance; never log its prompt/messages or bind it from client input. */
public record PreparedRun(ChatOwner owner,long sessionId,long runId,long userMessageId,long messageId,
                          String status,boolean duplicate,Instant deadline,List<ChatMessage> context) {
    @Override public String toString(){return "PreparedRun[runId="+runId+"]";}
}
