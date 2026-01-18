package io.datalens.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for chat memory with JVM protection.
 */
@Configuration
public class ChatMemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryConfig.class);

    @Value("${datalens.memory.max-messages-per-session:20}")
    private int maxMessagesPerSession;

    @Value("${datalens.memory.max-sessions:1000}")
    private int maxSessions;

    @Value("${datalens.memory.session-timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Bean
    public ChatMemory chatMemory() {
        return new BoundedInMemoryChatMemory(maxSessions, sessionTimeoutMinutes, maxMessagesPerSession);
    }

    /**
     * Bounded in-memory ChatMemory implementation using Caffeine cache.
     * Provides JVM protection with session limits and automatic cleanup.
     */
    public static class BoundedInMemoryChatMemory implements ChatMemory {

        private static final Logger log = LoggerFactory.getLogger(BoundedInMemoryChatMemory.class);

        private final Cache<String, List<Message>> sessionCache;
        private final AtomicInteger activeSessionCount = new AtomicInteger(0);
        private final int maxSessions;
        private final int maxMessagesPerSession;

        public BoundedInMemoryChatMemory(int maxSessions, int timeoutMinutes, int maxMessagesPerSession) {
            this.maxSessions = maxSessions;
            this.maxMessagesPerSession = maxMessagesPerSession;
            this.sessionCache = Caffeine.newBuilder()
                    .maximumSize(maxSessions)
                    .expireAfterAccess(timeoutMinutes, TimeUnit.MINUTES)
                    .removalListener((String key, List<Message> value, RemovalCause cause) -> {
                        activeSessionCount.decrementAndGet();
                        log.info("Session evicted: {} (cause: {})", key, cause);
                    })
                    .build();
        }

        @Override
        public void add(String conversationId, Message message) {
            add(conversationId, List.of(message));
        }

        @Override
        public void add(String conversationId, List<Message> messages) {
            List<Message> existing = sessionCache.getIfPresent(conversationId);

            if (existing == null) {
                if (activeSessionCount.get() >= maxSessions) {
                    log.warn("Max sessions reached ({}), oldest will be evicted", maxSessions);
                }
                activeSessionCount.incrementAndGet();
                existing = new ArrayList<>();
            } else {
                existing = new ArrayList<>(existing);
            }

            existing.addAll(messages);

            // Trim to max messages per session
            if (existing.size() > maxMessagesPerSession) {
                existing = new ArrayList<>(existing.subList(
                        existing.size() - maxMessagesPerSession,
                        existing.size()));
            }

            sessionCache.put(conversationId, existing);
        }

        @Override
        public List<Message> get(String conversationId, int lastN) {
            List<Message> messages = sessionCache.getIfPresent(conversationId);
            if (messages == null || messages.isEmpty()) {
                return new ArrayList<>();
            }

            int fromIndex = Math.max(0, messages.size() - lastN);
            return new ArrayList<>(messages.subList(fromIndex, messages.size()));
        }

        @Override
        public void clear(String conversationId) {
            sessionCache.invalidate(conversationId);
        }

        public int getActiveSessionCount() {
            return activeSessionCount.get();
        }

        public int getMaxSessions() {
            return maxSessions;
        }

        public void clearAll() {
            sessionCache.invalidateAll();
            activeSessionCount.set(0);
            log.info("All sessions cleared");
        }
    }
}
