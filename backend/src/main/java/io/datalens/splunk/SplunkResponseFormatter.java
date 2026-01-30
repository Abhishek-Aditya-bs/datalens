package io.datalens.splunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.Collectors;

public class SplunkResponseFormatter {

    private static final Set<String> PRIORITY_FIELDS = Set.of("_time", "host", "source", "sourcetype", "message", "_raw");
    private static final Set<String> SKIP_INTERNAL_FIELDS = Set.of("_bkt", "_cd", "_indextime", "_serial", "_si", "_subsecond", "_kv", "_sourcetype", "_pre_msg");

    private final ObjectMapper objectMapper;

    public SplunkResponseFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode formatQueryResponse(List<JsonNode> results, int maxResults, boolean truncated) {
        ObjectNode response = objectMapper.createObjectNode();

        // Clean results
        ArrayNode cleanedResults = objectMapper.createArrayNode();
        for (JsonNode result : results) {
            cleanedResults.add(cleanResult(result));
        }
        response.set("results", cleanedResults);
        response.put("totalResults", results.size());
        response.put("truncated", truncated);

        if (truncated) {
            response.put("truncation_guidance", "Results were capped at " + maxResults
                    + ". Narrow your time range or add filters to get complete data.");
        }

        // Generate analysis summary for non-trivial result sets
        if (results.size() > 10) {
            response.set("analysis_summary", buildAnalysisSummary(results));
        }

        return response;
    }

    private JsonNode cleanResult(JsonNode result) {
        ObjectNode cleaned = objectMapper.createObjectNode();
        Iterator<String> fieldNames = result.fieldNames();
        // Add priority fields first
        for (String field : PRIORITY_FIELDS) {
            if (result.has(field)) {
                cleaned.set(field, result.get(field));
            }
        }
        // Add remaining non-internal fields
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!PRIORITY_FIELDS.contains(field) && !field.startsWith("_") || field.equals("_raw")) {
                // _raw already added above if present
                if (!cleaned.has(field)) {
                    cleaned.set(field, result.get(field));
                }
            }
        }
        return cleaned;
    }

    private ObjectNode buildAnalysisSummary(List<JsonNode> results) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("eventCount", results.size());

        // Timeline
        String firstTime = null, lastTime = null;
        for (JsonNode r : results) {
            String time = r.path("_time").asText(null);
            if (time != null) {
                if (firstTime == null) firstTime = time;
                lastTime = time;
            }
        }
        if (firstTime != null) {
            ObjectNode timeline = objectMapper.createObjectNode();
            timeline.put("firstEvent", firstTime);
            timeline.put("lastEvent", lastTime);
            summary.set("timeline", timeline);
        }

        // Severity/level distribution
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        for (JsonNode r : results) {
            String severity = r.path("severity").asText(r.path("level").asText(r.path("log_level").asText(null)));
            if (severity != null && !severity.isEmpty()) {
                severityCounts.merge(severity, 1, Integer::sum);
            }
        }
        if (!severityCounts.isEmpty()) {
            summary.set("severityDistribution", objectMapper.valueToTree(severityCounts));
        }

        // Top hosts, sources, sourcetypes
        summary.set("topHosts", topValues(results, "host", 10));
        summary.set("topSources", topValues(results, "source", 10));
        summary.set("topSourcetypes", topValues(results, "sourcetype", 10));

        // Unique error messages
        Map<String, Integer> errorMessages = new LinkedHashMap<>();
        for (JsonNode r : results) {
            String msg = r.path("message").asText(null);
            if (msg == null) msg = r.path("_raw").asText(null);
            if (msg != null) {
                // Truncate long messages for summary
                String key = msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
                errorMessages.merge(key, 1, Integer::sum);
            }
        }
        if (!errorMessages.isEmpty()) {
            // Sort by count descending, take top 20
            List<Map.Entry<String, Integer>> sorted = errorMessages.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(20)
                    .collect(Collectors.toList());
            ArrayNode topErrors = objectMapper.createArrayNode();
            for (Map.Entry<String, Integer> entry : sorted) {
                topErrors.add(objectMapper.createObjectNode()
                        .put("message", entry.getKey())
                        .put("count", entry.getValue()));
            }
            summary.set("topMessages", topErrors);
        }

        return summary;
    }

    private ArrayNode topValues(List<JsonNode> results, String field, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode r : results) {
            String val = r.path(field).asText(null);
            if (val != null && !val.isEmpty()) {
                counts.merge(val, 1, Integer::sum);
            }
        }
        ArrayNode array = objectMapper.createArrayNode();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> array.add(objectMapper.createObjectNode()
                        .put("value", e.getKey())
                        .put("count", e.getValue())));
        return array;
    }
}
