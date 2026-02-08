package io.datalens.outlook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class OutlookResponseFormatter {

    private final ObjectMapper objectMapper;

    public OutlookResponseFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String formatEmailChainResponse(JsonNode response) {
        if (response == null) {
            return noResultsResponse("No response from Outlook.");
        }

        int totalEmails = response.path("summary").path("total_emails").asInt(0);
        if (totalEmails == 0) {
            String searchText = response.path("search_text").asText("");
            return noResultsResponse("No emails found matching '" + searchText + "'. "
                    + "Try broader search terms, check spelling, or search for incident IDs, "
                    + "error codes, or participant names.");
        }

        // Pass through the structured response as-is — it's already well-formed
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"error\":\"Failed to format response: " + e.getMessage() + "\"}";
        }
    }

    public String formatConnectionResponse(JsonNode response) {
        if (response == null) {
            return "{\"status\":\"error\",\"error\":\"No response from Outlook connection check.\"}";
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"error\":\"Failed to format connection response: " + e.getMessage() + "\"}";
        }
    }

    private String noResultsResponse(String guidance) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "success");
        response.put("total_emails", 0);
        response.put("guidance", guidance);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":\"success\",\"total_emails\":0,\"guidance\":\"" + guidance + "\"}";
        }
    }
}
