package io.datalens.config;

import io.datalens.tools.BitbucketTools;
import io.datalens.tools.DatabaseTools;
import io.datalens.tools.OutlookTools;
import io.datalens.tools.SplunkTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Autowired(required = false)
    private SplunkTools splunkTools;

    @Autowired(required = false)
    private BitbucketTools bitbucketTools;

    @Autowired(required = false)
    private OutlookTools outlookTools;

    /**
     * Extract tool callbacks from DatabaseTools (and SplunkTools if available) for manual tool execution.
     */
    @Bean
    public List<ToolCallback> databaseToolCallbacks(DatabaseTools databaseTools) {
        List<Object> toolObjects = new ArrayList<>();
        toolObjects.add(databaseTools);
        if (splunkTools != null) {
            toolObjects.add(splunkTools);
        }
        if (bitbucketTools != null) {
            toolObjects.add(bitbucketTools);
        }
        if (outlookTools != null) {
            toolObjects.add(outlookTools);
        }

        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects.toArray())
                .build();
        return List.of(provider.getToolCallbacks());
    }

    /**
     * Creates a ChatClient with database tools (and Splunk tools if available) and conversation memory.
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  DatabaseTools databaseTools,
                                  ChatMemory chatMemory) throws IOException {
        String systemPrompt = loadSystemPrompt();

        List<Object> tools = new ArrayList<>();
        tools.add(databaseTools);
        if (splunkTools != null) {
            tools.add(splunkTools);
        }
        if (bitbucketTools != null) {
            tools.add(bitbucketTools);
        }
        if (outlookTools != null) {
            tools.add(outlookTools);
        }

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultTools(tools.toArray())
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
