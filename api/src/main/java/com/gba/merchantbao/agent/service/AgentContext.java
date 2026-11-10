package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.chat.entity.ChatMessageRow;
import dev.langchain4j.data.message.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public final class AgentContext {
    private final AgentProperties p;
    public AgentContext(AgentProperties p) { this.p=p; }
    public List<ChatMessage> assemble(List<ChatMessageRow> history, String input) {
        int available=p.getContextTokens()-p.getMaxOutputTokens()-cost(p.getSystemPrompt())-cost(input);
        if(available<0)throw new IllegalArgumentException("AGENT_CONTEXT_TOO_LARGE");
        var ordered=history.stream().sorted(Comparator.comparingLong(ChatMessageRow::messageSeq)).toList();
        var selected=new LinkedList<ChatMessage>();
        for(int i=ordered.size()-1;i>0;i--) {
            var a=ordered.get(i); var u=ordered.get(i-1);
            if(!a.senderType().equals("ASSISTANT") || !a.status().equals("COMPLETED")
                    || !u.senderType().equals("USER") || !u.status().equals("COMPLETED")
                    || u.runId()!=a.runId() || u.messageSeq()+1!=a.messageSeq())continue;
            int budget=cost(u.content())+cost(a.content());
            if(selected.size()+2>p.getHistoryMessages() || budget>available)break;
            selected.addFirst(AiMessage.from(a.content())); selected.addFirst(UserMessage.from(u.content()));
            available-=budget; i--;
        }
        selected.addFirst(SystemMessage.from(p.getSystemPrompt())); selected.addLast(UserMessage.from(input));
        return List.copyOf(selected);
    }
    /** Conservative approximation: one UTF-8 byte per token plus role overhead, not billable usage. */
    private static int cost(String text){return text.getBytes(StandardCharsets.UTF_8).length+4;}
}
