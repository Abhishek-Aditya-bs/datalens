package io.datalens.config;

import io.datalens.tools.DatabaseTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Configuration for the ChatClient with tools and memory.
 */
@Configuration
public class ChatClientConfig {

    @Value("${datalens.prompt.file:classpath:prompts/system-prompt.txt}")
    private Resource systemPromptResource;

    @Value("${datalens.schema.default:SCHEMA_A}")
    private String defaultSchema;

    @Value("${datalens.schema.secondary:SCHEMA_B}")
    private String secondarySchema;

    /**
     * Extract tool callbacks from DatabaseTools for manual tool execution.
     */
    @Bean
    public List<ToolCallback> databaseToolCallbacks(DatabaseTools databaseTools) {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(databaseTools)
                .build();
        return List.of(provider.getToolCallbacks());
    }

    /**
     * Creates a ChatClient with database tools and conversation memory.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  DatabaseTools databaseTools,
                                  ChatMemory chatMemory) throws IOException {
        String systemPrompt = loadSystemPrompt();

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultTools(databaseTools)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    private String loadSystemPrompt() throws IOException {
        String prompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        return prompt
                .replace("{{defaultSchema}}", defaultSchema)
                .replace("{{secondarySchema}}", secondarySchema);
    }
}
