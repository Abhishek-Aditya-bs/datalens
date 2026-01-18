package io.datalens.config;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI configuration using standard API key authentication.
 * Activated when datalens.llm.provider=openai
 * API key is configured via spring.ai.openai.api-key property.
 */
@Configuration
@ConditionalOnProperty(name = "datalens.llm.provider", havingValue = "openai")
public class OpenAiConfig {

    /**
     * Custom chat options for OpenAI.
     */
    @Bean
    @ConditionalOnProperty(name = "datalens.llm.provider", havingValue = "openai")
    public OpenAiChatOptions openAiChatOptions() {
        return OpenAiChatOptions.builder()
                .model("gpt-4")
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }
}
