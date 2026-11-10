package com.gba.merchantbao.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.net.URI;
import java.util.List;

@Getter @Setter
@ConfigurationProperties("merchant.agent")
public class AgentProperties {
    private boolean enabled;
    private String baseUrl, apiKey, model;
    private String apiKeyHeader = "Authorization";
    private String serviceLabel = "openai-compatible";
    private String promptVersion = "assistant-v1";
    private String systemPrompt = "你是商户助手。请准确、简洁地回答；没有依据时明确说明。";
    private double temperature = 0.2;
    private int maxInputChars = 4000, maxOutputChars = 16000, maxOutputTokens = 2048;
    private int contextTokens = 8192, historyMessages = 20, maxPageSize = 100;
    private int maxRetries = 1, workers = 8, queueCapacity = 64, streamQueueCapacity = 64;
    private Duration connectTimeout = Duration.ofSeconds(5), firstTokenTimeout = Duration.ofSeconds(20);
    private Duration totalTimeout = Duration.ofSeconds(90), heartbeat = Duration.ofSeconds(10);
    private Duration retryBackoff = Duration.ofMillis(200), writeTimeout = Duration.ofSeconds(5);
    private List<String> allowedOrigins = List.of();

    public void validate() {
        for (String origin : allowedOrigins) {
            URI uri = uri(origin);
            require(!origin.contains("*") && uri.getHost() != null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty()), "allowed-origins");
        }
        require(workers > 0 && workers <= 64 && queueCapacity > 0 && queueCapacity <= 1024
                && streamQueueCapacity > 0 && streamQueueCapacity <= 256, "execution-limits");
        for (Duration d : java.util.Arrays.asList(connectTimeout, firstTokenTimeout, totalTimeout, heartbeat, retryBackoff, writeTimeout))
            require(d != null && d.toMillis() > 0 && d.compareTo(Duration.ofMinutes(5)) <= 0, "timeouts");
        if (!enabled) return;
        URI root = uri(baseUrl);
        require(root.getHost() != null && root.getUserInfo() == null && root.getRawQuery() == null
                && root.getFragment() == null && !root.getPath().contains("chat/completions"), "base-url");
        require(real(apiKey), "api-key");
        require(apiKeyHeader != null && apiKeyHeader.matches("[A-Za-z][A-Za-z0-9-]{0,63}"), "api-key-header");
        require(real(model) && model.length() <= 128 && label(model), "model");
        require(label(serviceLabel) && serviceLabel.length() <= 64, "service-label");
        require(label(promptVersion) && promptVersion.length() <= 64 && real(systemPrompt), "prompt-version/system-prompt");
        require(Double.isFinite(temperature) && temperature >= 0 && temperature <= 2, "temperature");
        require(maxInputChars > 0 && maxInputChars <= 4000 && maxOutputChars > 0 && maxOutputChars <= 64000, "input/output-chars");
        require(maxOutputTokens > 0 && maxOutputTokens <= 32768 && contextTokens > maxOutputTokens
                && contextTokens <= 131072, "context/output-tokens");
        require(historyMessages >= 0 && historyMessages <= 100 && maxPageSize > 0 && maxPageSize <= 100, "history/page-size");
        require(maxRetries >= 0 && maxRetries <= 3 && workers > 0 && workers <= 64 && queueCapacity > 0
                && queueCapacity <= 1024 && streamQueueCapacity > 0 && streamQueueCapacity <= 256, "execution-limits");
        require(connectTimeout.compareTo(totalTimeout) <= 0 && firstTokenTimeout.compareTo(totalTimeout) <= 0
                && heartbeat.compareTo(totalTimeout) < 0, "timeouts");
    }
    private static URI uri(String s) {
        try { URI u = URI.create(s); require(List.of("http", "https").contains(u.getScheme()), "URL"); return u; }
        catch (RuntimeException e) { throw new IllegalStateException("Invalid merchant.agent URL"); }
    }
    private static boolean real(String s) {
        if (s == null || s.isBlank()) return false;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains("your_") && !lower.contains("replace") && !lower.contains("changeme")
                && !lower.contains("placeholder") && !lower.contains("${") && !lower.startsWith("<");
    }
    private static boolean label(String s) { return s != null && s.matches("[A-Za-z0-9._:/-]+"); }
    private static void require(boolean ok, String field) {
        if (!ok) throw new IllegalStateException("Invalid merchant.agent " + field);
    }
}
