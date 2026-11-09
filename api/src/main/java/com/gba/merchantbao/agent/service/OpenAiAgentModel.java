package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** SDK owns request serialization and SSE parsing; each call owns a cancellable transport. */
public final class OpenAiAgentModel implements AgentModel {
    private final AgentProperties properties;
    public OpenAiAgentModel(AgentProperties properties) { this.properties = properties; }
    @Override public Call stream(List<ChatMessage> messages, Listener listener) {
        var cancelled = new AtomicBoolean();
        var handle = new AtomicReference<StreamingHandle>();
        var transport = new OwnedHttpClientBuilder();
        boolean bearer = "Authorization".equalsIgnoreCase(properties.getApiKeyHeader());
        var model = OpenAiStreamingChatModel.builder().baseUrl(properties.getBaseUrl())
                .apiKey(bearer ? properties.getApiKey() : null)
                .customHeaders(bearer ? Map.of() : Map.of(properties.getApiKeyHeader(), properties.getApiKey()))
                .modelName(properties.getModel()).temperature(properties.getTemperature())
                .maxTokens(properties.getMaxOutputTokens()).timeout(properties.getTotalTimeout())
                .httpClientBuilder(JdkHttpClient.builder().httpClientBuilder(transport)
                        .connectTimeout(properties.getConnectTimeout()).readTimeout(properties.getTotalTimeout()))
                .logRequests(false).logResponses(false).build();
        model.chat(messages, new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(PartialResponse response, PartialResponseContext context) {
                handle.set(context.streamingHandle());
                if (cancelled.get()) { context.streamingHandle().cancel(); return; }
                listener.delta(response.text());
            }
            @Override public void onCompleteResponse(ChatResponse response) {
                try {
                    if (!cancelled.get()) {
                        var u = response.tokenUsage();
                        listener.complete(u == null || (u.inputTokenCount() == null && u.outputTokenCount() == null)
                                ? null : new Usage(u.inputTokenCount() == null ? null : u.inputTokenCount().longValue(),
                                u.outputTokenCount() == null ? null : u.outputTokenCount().longValue()));
                    }
                } finally { transport.stop(); }
            }
            @Override public void onError(Throwable error) {
                try { if (!cancelled.get()) listener.error(error); } finally { transport.stop(); }
            }
        });
        return () -> { cancelled.set(true); var h=handle.get(); if(h!=null)h.cancel(); transport.stop(); };
    }
}
