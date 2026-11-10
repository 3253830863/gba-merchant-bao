package com.gba.merchantbao.agent.service;

import com.gba.merchantbao.agent.config.AgentProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.HttpException;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded pre-body retries share one durable deadline. Each attempt accepts one terminal callback. */
@Component
public final class AgentAttempts {
    private final AgentModel model;
    private final AgentProperties properties;
    private final ScheduledExecutorService timer;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentAttempts(AgentModel model, AgentProperties properties, Clock clock) {
        this(model, properties, timer(), clock);
    }
    public AgentAttempts(AgentModel model, AgentProperties properties, ScheduledExecutorService timer, Clock clock) {
        this.model = model; this.properties = properties; this.timer = timer; this.clock = clock;
    }
    private static ScheduledExecutorService timer() {
        var timer = new ScheduledThreadPoolExecutor(1, r -> {
            var thread = new Thread(r, "agent-retry-timer"); thread.setDaemon(true); return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
    public AgentModel.Call stream(List<ChatMessage> context, Instant deadline, AgentModel.Listener downstream) {
        var stopped = new AtomicBoolean();
        var body = new AtomicBoolean();
        var attempt = new AtomicInteger();
        var calls = new CopyOnWriteArrayList<AgentModel.Call>();
        var scheduled = new CopyOnWriteArrayList<ScheduledFuture<?>>();
        class Execution {
            void cleanup() {
                scheduled.forEach(f -> f.cancel(false));
                calls.forEach(AgentModel.Call::cancel);
            }
            void schedule(Runnable action, long delay) {
                var future = timer.schedule(action, delay, TimeUnit.MILLISECONDS);
                scheduled.add(future);
                if (stopped.get()) future.cancel(false);
            }
            void timeout() {
                if (stopped.compareAndSet(false, true)) {
                    cleanup(); downstream.error(new TimeoutException("Agent response timed out"));
                }
            }
            void start() {
                if (stopped.get()) return;
                if (!clock.instant().isBefore(deadline)) { timeout(); return; }
                int number = attempt.incrementAndGet();
                var ended = new AtomicBoolean();
                var handle = new AtomicReference<AgentModel.Call>();
                AgentModel.Listener listener = new AgentModel.Listener() {
                    public void delta(String text) {
                        if (!stopped.get() && !ended.get() && text != null && !text.isEmpty()) {
                            body.set(true); downstream.delta(text);
                        }
                    }
                    public void complete(AgentModel.Usage usage) {
                        if (ended.compareAndSet(false, true) && stopped.compareAndSet(false, true)) {
                            cleanup(); downstream.complete(usage);
                        }
                    }
                    public void error(Throwable error) {
                        if (!ended.compareAndSet(false, true) || stopped.get()) return;
                        var call = handle.get(); if (call != null) call.cancel();
                        if (!body.get() && retryable(error) && number <= properties.getMaxRetries()
                                && clock.instant().plus(properties.getRetryBackoff()).isBefore(deadline)) {
                            schedule(Execution.this::start, properties.getRetryBackoff().toMillis());
                        } else if (stopped.compareAndSet(false, true)) {
                            cleanup(); downstream.error(error);
                        }
                    }
                };
                try {
                    var call = model.stream(context, listener);
                    handle.set(call); calls.add(call);
                    // A synchronous callback or timeout may finish before the transport returns its handle.
                    if (stopped.get() || ended.get()) call.cancel();
                } catch (RuntimeException error) { listener.error(error); }
            }
        }
        var execution = new Execution();
        long remaining = Math.max(1, Duration.between(clock.instant(), deadline).toMillis());
        execution.schedule(execution::timeout, remaining);
        execution.schedule(() -> { if (!body.get()) execution.timeout(); },
                Math.min(properties.getFirstTokenTimeout().toMillis(), remaining));
        execution.start();
        return () -> { stopped.set(true); execution.cleanup(); };
    }
    private static boolean retryable(Throwable error) {
        if (error instanceof HttpException http)
            return http.statusCode() == 429 || (http.statusCode() >= 500 && http.statusCode() < 600);
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof java.io.IOException) return true;
        return false;
    }
    @jakarta.annotation.PreDestroy void shutdown() { timer.shutdownNow(); }
}
