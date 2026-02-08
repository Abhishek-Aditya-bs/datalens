package io.datalens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.datalens.telemetry.TelemetryService;
import io.datalens.tools.BitbucketTools;
import io.datalens.tools.DatabaseTools;
import io.datalens.tools.OutlookTools;
import io.datalens.tools.SplunkTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for handling chat interactions with the LLM.
 * Uses manual tool execution to emit tool call events for frontend visibility.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final DatabaseTools databaseTools;
    private final SplunkTools splunkTools;
    private final BitbucketTools bitbucketTools;
    private final OutlookTools outlookTools;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;

    public ChatService(ChatClient chatClient,
                       ChatModel chatModel,
                       ChatMemory chatMemory,
                       DatabaseTools databaseTools,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) SplunkTools splunkTools,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) BitbucketTools bitbucketTools,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) OutlookTools outlookTools,
                       TelemetryService telemetryService,
                       ObjectMapper objectMapper,
                       List<ToolCallback> databaseToolCallbacks,
                       @Value("${datalens.prompt.file:classpath:prompts/system-prompt.txt}") Resource systemPromptResource,
                       @Value("${datalens.schema.default:SCHEMA_A}") String defaultSchema,
                       @Value("${datalens.schema.secondary:SCHEMA_B}") String secondarySchema) throws IOException {
        this.chatClient = chatClient;
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.databaseTools = databaseTools;
        this.splunkTools = splunkTools;
        this.bitbucketTools = bitbucketTools;
        this.outlookTools = outlookTools;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
        this.toolCallbacks = databaseToolCallbacks;
        this.systemPrompt = loadSystemPrompt(systemPromptResource, defaultSchema, secondarySchema);
    }

    private String loadSystemPrompt(Resource resource, String defaultSchema, String secondarySchema) throws IOException {
        String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
        return prompt
                .replace("{{defaultSchema}}", defaultSchema)
                .replace("{{secondarySchema}}", secondarySchema);
    }

    public Flux<String> streamChat(String message, String sessionId) {
        log.info("Processing chat message for session: {}", sessionId);

        return chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param("chat_memory_conversation_id", sessionId))
                .stream()
                .content()
                .doOnSubscribe(s -> log.debug("Starting stream for session: {}", sessionId))
                .doOnComplete(() -> log.debug("Completed stream for session: {}", sessionId))
                .doOnError(e -> log.error("Error in stream for session {}: {}", sessionId, e.getMessage()));
    }

    /**
     * Stream chat with manual tool execution - emits tool call events for frontend visibility.
     */
    public Flux<String> streamChatWithToolEvents(String message, String sessionId) {
        log.info("Processing chat with tool events for session: {}", sessionId);

        return Flux.create(sink -> {
            try {
                // Get conversation history from memory
                List<Message> conversationHistory = new ArrayList<>();
                List<Message> previousMessages = chatMemory.get(sessionId, 50);
                if (previousMessages != null) {
                    conversationHistory.addAll(previousMessages);
                }

                // Add the new user message
                UserMessage userMessage = new UserMessage(message);
                conversationHistory.add(userMessage);

                // Process with tool loop
                processWithToolLoop(conversationHistory, sessionId, sink);

            } catch (Exception e) {
                log.error("Error in streamChatWithToolEvents: {}", e.getMessage(), e);
                sink.next(formatErrorEvent(e.getMessage()));
                sink.complete();
            }
        });
    }

    private void processWithToolLoop(List<Message> messages, String sessionId,
                                     reactor.core.publisher.FluxSink<String> sink) {
        int iteration = 0;

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++;
            log.debug("Tool loop iteration {} for session {}", iteration, sessionId);

            try {
                // Build prompt with system message and conversation history
                List<Message> promptMessages = new ArrayList<>();
                promptMessages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
                promptMessages.addAll(messages);

                // Create prompt with tool calling options
                ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                        .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                        .internalToolExecutionEnabled(false) // We handle tool execution manually
                        .build();

                Prompt prompt = new Prompt(promptMessages, options);

                // Call the model
                ChatResponse response = chatModel.call(prompt);
                Generation generation = response.getResult();
                AssistantMessage assistantMessage = generation.getOutput();

                // Check if there are tool calls
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    log.info("LLM requested {} tool call(s)", assistantMessage.getToolCalls().size());

                    // Add assistant message to history
                    messages.add(assistantMessage);

                    // Process each tool call
                    List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

                    for (var toolCall : assistantMessage.getToolCalls()) {
                        String toolCallId = toolCall.id();
                        String toolName = toolCall.name();
                        String arguments = toolCall.arguments();

                        log.info("Executing tool: {} with args: {}", toolName, arguments);

                        // Emit tool call event to frontend
                        sink.next(formatToolCallEvent(toolCallId, toolName, arguments));

                        // Execute the tool
                        String toolResult = executeToolCall(toolName, arguments);

                        // Emit tool result event to frontend
                        sink.next(formatToolResultEvent(toolCallId, toolName, toolResult));

                        toolResponses.add(new ToolResponseMessage.ToolResponse(toolCallId, toolName, toolResult));
                    }

                    // Add tool response message to history
                    ToolResponseMessage toolResponseMessage = new ToolResponseMessage(toolResponses, Map.of());
                    messages.add(toolResponseMessage);

                    // Continue the loop to get the final response
                    continue;
                }

                // No tool calls - stream the final text response
                String textContent = assistantMessage.getText();
                if (textContent != null && !textContent.isEmpty()) {
                    // Stream the text in chunks for better UX
                    for (int i = 0; i < textContent.length(); i += 50) {
                        int end = Math.min(i + 50, textContent.length());
                        sink.next(formatTextEvent(textContent.substring(i, end)));
                    }
                }

                // Update chat memory with the full conversation
                messages.add(assistantMessage);
                chatMemory.add(sessionId, messages);

                // Emit finish event
                sink.next(formatFinishEvent("STOP"));
                sink.complete();
                return;

            } catch (Exception e) {
                log.error("Error in tool loop iteration {}: {}", iteration, e.getMessage(), e);
                sink.next(formatErrorEvent(e.getMessage()));
                sink.complete();
                return;
            }
        }

        // Max iterations reached
        log.warn("Max tool iterations ({}) reached for session {}", MAX_TOOL_ITERATIONS, sessionId);
        sink.next(formatTextEvent("I apologize, but I've reached the maximum number of tool calls. Please try rephrasing your request."));
        sink.next(formatFinishEvent("MAX_ITERATIONS"));
        sink.complete();
    }

    private String executeToolCall(String toolName, String argumentsJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argumentsJson, Map.class);

            return switch (toolName) {
                case "executeQuery" -> databaseTools.executeQuery((String) args.get("sql"));
                case "connectToEnvironment" -> databaseTools.connectToEnvironment((String) args.get("env"));
                case "getCurrentStatus" -> databaseTools.getCurrentStatus();
                case "getTableSchema" -> databaseTools.getTableSchema(
                        (String) args.get("tableName"),
                        (String) args.get("schema")
                );
                case "listTables" -> databaseTools.listTables((String) args.get("schema"));
                case "splunkCheckConnection" -> {
                    if (splunkTools == null) yield "{\"error\": \"Splunk tools not available. Enable the splunk profile.\"}";
                    yield splunkTools.splunkCheckConnection();
                }
                case "splunkGetIndexForEnvironment" -> {
                    if (splunkTools == null) yield "{\"error\": \"Splunk tools not available. Enable the splunk profile.\"}";
                    yield splunkTools.splunkGetIndexForEnvironment((String) args.get("env"));
                }
                case "splunkExecuteQuery" -> {
                    if (splunkTools == null) yield "{\"error\": \"Splunk tools not available. Enable the splunk profile.\"}";
                    Integer maxResults = args.get("maxResults") != null ? ((Number) args.get("maxResults")).intValue() : null;
                    yield splunkTools.splunkExecuteQuery(
                            (String) args.get("query"),
                            (String) args.get("earliestTime"),
                            (String) args.get("latestTime"),
                            maxResults
                    );
                }
                case "splunkGetAvailableIndexes" -> {
                    if (splunkTools == null) yield "{\"error\": \"Splunk tools not available. Enable the splunk profile.\"}";
                    yield splunkTools.splunkGetAvailableIndexes();
                }
                case "splunkGetSourcetypes" -> {
                    if (splunkTools == null) yield "{\"error\": \"Splunk tools not available. Enable the splunk profile.\"}";
                    yield splunkTools.splunkGetSourcetypes((String) args.get("index"));
                }
                case "bitbucketCheckConnection" -> {
                    if (bitbucketTools == null) yield "{\"error\": \"Bitbucket tools not available. Enable the bitbucket profile.\"}";
                    yield bitbucketTools.bitbucketCheckConnection();
                }
                case "bitbucketSearchCode" -> {
                    if (bitbucketTools == null) yield "{\"error\": \"Bitbucket tools not available. Enable the bitbucket profile.\"}";
                    Integer maxResults = args.get("maxResults") != null ? ((Number) args.get("maxResults")).intValue() : null;
                    yield bitbucketTools.bitbucketSearchCode((String) args.get("query"), maxResults);
                }
                case "bitbucketReadFile" -> {
                    if (bitbucketTools == null) yield "{\"error\": \"Bitbucket tools not available. Enable the bitbucket profile.\"}";
                    yield bitbucketTools.bitbucketReadFile(
                            (String) args.get("repoSlug"),
                            (String) args.get("filePath"),
                            (String) args.get("branch")
                    );
                }
                case "outlookCheckConnection" -> {
                    if (outlookTools == null) yield "{\"error\": \"Outlook tools not available. Enable the outlook profile.\"}";
                    yield outlookTools.outlookCheckConnection();
                }
                case "outlookGetEmailChain" -> {
                    if (outlookTools == null) yield "{\"error\": \"Outlook tools not available. Enable the outlook profile.\"}";
                    Boolean includePersonal = args.get("includePersonal") != null ? (Boolean) args.get("includePersonal") : null;
                    Boolean includeShared = args.get("includeShared") != null ? (Boolean) args.get("includeShared") : null;
                    yield outlookTools.outlookGetEmailChain(
                            (String) args.get("searchText"),
                            includePersonal,
                            includeShared
                    );
                }
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return "{\"error\": \"Tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    // Data protocol format methods
    private String formatTextEvent(String text) {
        return "0:\"" + escapeJson(text) + "\"\n";
    }

    private String formatToolCallEvent(String toolCallId, String toolName, String args) {
        return "9:{\"toolCallId\":\"" + toolCallId + "\",\"toolName\":\"" + toolName + "\",\"args\":" + args + "}\n";
    }

    private String formatToolResultEvent(String toolCallId, String toolName, String result) {
        return "a:{\"toolCallId\":\"" + toolCallId + "\",\"toolName\":\"" + toolName + "\",\"result\":" + escapeJsonString(result) + "}\n";
    }

    private String formatFinishEvent(String reason) {
        return "d:{\"finishReason\":\"" + reason + "\"}\n";
    }

    private String formatErrorEvent(String errorMessage) {
        return "e:{\"error\":\"" + escapeJson(errorMessage) + "\"}\n";
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeJsonString(String text) {
        if (text == null) return "null";
        // If it's already valid JSON, return as-is
        try {
            objectMapper.readTree(text);
            return text;
        } catch (Exception e) {
            // Not valid JSON, wrap as string
            return "\"" + escapeJson(text) + "\"";
        }
    }

    public Flux<ChatResponse> streamChatWithTools(String message, String sessionId) {
        log.info("Processing chat with tools for session: {}", sessionId);

        return chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param("chat_memory_conversation_id", sessionId))
                .stream()
                .chatResponse()
                .doOnSubscribe(s -> log.debug("Starting tool-enabled stream for session: {}", sessionId))
                .doOnComplete(() -> log.debug("Completed tool-enabled stream for session: {}", sessionId))
                .doOnError(e -> log.error("Error in tool stream for session {}: {}", sessionId, e.getMessage()));
    }

    public String chat(String message, String sessionId) {
        log.info("Processing non-streaming chat for session: {}", sessionId);

        ChatResponse response = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param("chat_memory_conversation_id", sessionId))
                .call()
                .chatResponse();

        AssistantMessage assistantMessage = response.getResult().getOutput();
        return assistantMessage.getText();
    }
}
