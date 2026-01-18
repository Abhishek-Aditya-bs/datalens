package io.datalens.controller;

import io.datalens.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for chat endpoints.
 */
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        String sessionId = getOrCreateSessionId(request.sessionId());
        log.info("Received streaming chat request for session: {}", sessionId);

        // Return plain text chunks - Spring WebFlux adds SSE formatting automatically
        return chatService.streamChat(request.message(), sessionId);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatWithTools(@RequestBody ChatRequest request) {
        String sessionId = getOrCreateSessionId(request.sessionId());
        log.info("Received tool-enabled streaming request for session: {}", sessionId);

        // Use the new method that emits tool call events
        return chatService.streamChatWithToolEvents(request.message(), sessionId);
    }

    @PostMapping("/chat/sync")
    public ResponseEntity<ChatSyncResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = getOrCreateSessionId(request.sessionId());
        log.info("Received sync chat request for session: {}", sessionId);

        String response = chatService.chat(request.message(), sessionId);
        return ResponseEntity.ok(new ChatSyncResponse(sessionId, response));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "DataLens Backend",
                "version", "1.0.0"
        ));
    }

    private String getOrCreateSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;
    }

    private String formatDataProtocolResponse(org.springframework.ai.chat.model.ChatResponse response) {
        if (response.getResult() == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        var output = response.getResult().getOutput();

        if (output.getText() != null && !output.getText().isEmpty()) {
            sb.append("0:\"").append(escapeJson(output.getText())).append("\"\n");
        }

        if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
            for (var toolCall : output.getToolCalls()) {
                sb.append("9:{\"toolCallId\":\"")
                        .append(toolCall.id())
                        .append("\",\"toolName\":\"")
                        .append(toolCall.name())
                        .append("\",\"args\":")
                        .append(toolCall.arguments())
                        .append("}\n");
            }
        }

        var finishReason = response.getResult().getMetadata().getFinishReason();
        if (finishReason != null && !"TOOL_CALLS".equals(finishReason)) {
            sb.append("d:{\"finishReason\":\"").append(finishReason).append("\"}\n");
        }

        return sb.toString();
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record ChatRequest(String message, String sessionId) {}
    public record ChatSyncResponse(String sessionId, String response) {}
}
