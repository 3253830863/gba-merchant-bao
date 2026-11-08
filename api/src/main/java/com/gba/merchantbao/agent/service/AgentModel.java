package com.gba.merchantbao.agent.service;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/** One model per deployment. Application lifecycle and persistence stay outside the SDK adapter. */
public interface AgentModel {
    Call stream(List<ChatMessage> messages, Listener listener);
    interface Call { void cancel(); default void fail(){cancel();} }
    interface Listener {
        void delta(String text);
        void complete(Usage usage);
        void error(Throwable error);
    }
    record Usage(Long inputTokens, Long outputTokens) { }
}
