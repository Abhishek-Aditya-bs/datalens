package io.datalens.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datalens.outlook.OutlookClient;
import io.datalens.outlook.OutlookResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("outlook")
public class OutlookTools {

    private static final Logger log = LoggerFactory.getLogger(OutlookTools.class);

    private final OutlookClient outlookClient;
    private final ObjectMapper objectMapper;
    private final OutlookResponseFormatter formatter;

    public OutlookTools(OutlookClient outlookClient, ObjectMapper objectMapper) {
        this.outlookClient = outlookClient;
        this.objectMapper = objectMapper;
        this.formatter = new OutlookResponseFormatter(objectMapper);
    }

    @Tool(description = "Check the connection to Outlook Desktop and verify mailbox access. "
            + "Returns Outlook version, personal mailbox status, and shared mailbox status. "
            + "Use this to verify Outlook is running and accessible before searching emails.")
    public String outlookCheckConnection() {
        log.info("Checking Outlook connection");
        try {
            JsonNode result = outlookClient.checkConnection();
            return formatter.formatConnectionResponse(result);
        } catch (Exception e) {
            log.error("Outlook connection check failed: {}", e.getMessage(), e);
            return toJson(Map.of("status", "error", "connected", false, "error", e.getMessage()));
        }
    }

    @Tool(description = "Search for emails in Outlook Desktop and return grouped email conversations. "
            + "Searches both subject lines and email bodies using case-insensitive phrase matching. "
            + "Searches both personal and shared mailboxes by default. "
            + "Returns emails grouped by conversation thread with a summary including participant list, "
            + "date range, and mailbox distribution. "
            + "Great for finding incident email chains - search by incident ID (e.g. 'INC-12345'), "
            + "error codes (e.g. 'ORA-00060'), deployment topics, or participant names. "
            + "Use outlookCheckConnection first to verify Outlook is accessible.")
    public String outlookGetEmailChain(
            @ToolParam(description = "Text to search for in email subjects and bodies. "
                    + "Uses case-insensitive phrase matching. Examples: 'INC-12345', 'ORA-00060 deadlock', "
                    + "'deployment rollback production'") String searchText,
            @ToolParam(description = "Whether to search the personal inbox. Optional, defaults to true.") Boolean includePersonal,
            @ToolParam(description = "Whether to search the shared/team mailbox. Optional, defaults to true.") Boolean includeShared) {
        log.info("Searching Outlook emails: searchText='{}', personal={}, shared={}", searchText, includePersonal, includeShared);
        try {
            boolean personal = (includePersonal == null || includePersonal);
            boolean shared = (includeShared == null || includeShared);

            JsonNode result = outlookClient.searchEmails(searchText, personal, shared);
            return formatter.formatEmailChainResponse(result);
        } catch (Exception e) {
            log.error("Outlook email search failed: {}", e.getMessage(), e);
            return toJson(Map.of("status", "error", "error", "Email search failed: " + e.getMessage()));
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
