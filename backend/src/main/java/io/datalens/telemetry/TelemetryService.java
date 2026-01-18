package io.datalens.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom telemetry service for tracking DataLens metrics.
 */
@Component
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> requestCounters = new ConcurrentHashMap<>();

    public TelemetryService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordChatRequest(String environment, String toolUsed) {
        String tool = (toolUsed != null && !toolUsed.isBlank()) ? toolUsed : "none";

        Counter.builder("datalens.chat.requests")
                .description("Total chat requests")
                .tag("environment", environment)
                .tag("tool", tool)
                .register(meterRegistry)
                .increment();

        String key = environment + ":" + tool;
        requestCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

        log.debug("Recorded chat request - env: {}, tool: {}", environment, tool);
    }

    public void recordQueryDuration(String environment, long durationMs) {
        Timer.builder("datalens.db.query.duration")
                .description("Database query execution time")
                .tag("environment", environment)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        log.debug("Recorded query duration - env: {}, duration: {}ms", environment, durationMs);
    }

    public void recordError(String errorType, String source) {
        Counter.builder("datalens.errors")
                .description("Error occurrences")
                .tag("type", errorType)
                .tag("source", source)
                .register(meterRegistry)
                .increment();

        log.debug("Recorded error - type: {}, source: {}", errorType, source);
    }

    public void recordTokenUsage(long promptTokens, long completionTokens) {
        Counter.builder("datalens.llm.tokens")
                .description("LLM token usage")
                .tag("type", "prompt")
                .register(meterRegistry)
                .increment(promptTokens);

        Counter.builder("datalens.llm.tokens")
                .description("LLM token usage")
                .tag("type", "completion")
                .register(meterRegistry)
                .increment(completionTokens);

        log.debug("Recorded token usage - prompt: {}, completion: {}", promptTokens, completionTokens);
    }

    public long getRequestCount(String environment) {
        return requestCounters.entrySet().stream()
                .filter(e -> e.getKey().startsWith(environment + ":"))
                .mapToLong(e -> e.getValue().get())
                .sum();
    }

    public long getToolRequestCount(String toolName) {
        return requestCounters.entrySet().stream()
                .filter(e -> e.getKey().endsWith(":" + toolName))
                .mapToLong(e -> e.getValue().get())
                .sum();
    }
}
