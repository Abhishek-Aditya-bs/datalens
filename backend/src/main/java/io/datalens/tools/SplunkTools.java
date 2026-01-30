package io.datalens.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datalens.splunk.SplunkClient;
import io.datalens.splunk.SplunkConfig;
import io.datalens.splunk.SplunkResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("splunk")
public class SplunkTools {

    private static final Logger log = LoggerFactory.getLogger(SplunkTools.class);

    private final SplunkClient splunkClient;
    private final SplunkConfig splunkConfig;
    private final ObjectMapper objectMapper;
    private final SplunkResponseFormatter formatter;

    public SplunkTools(SplunkClient splunkClient, SplunkConfig splunkConfig, ObjectMapper objectMapper) {
        this.splunkClient = splunkClient;
        this.splunkConfig = splunkConfig;
        this.objectMapper = objectMapper;
        this.formatter = new SplunkResponseFormatter(objectMapper);
    }

    @Tool(description = """
        Check the connection to Splunk and return server information.
        Returns server name, version, OS, and connection status.
        Use this to verify Splunk is accessible before running queries.
        """)
    public String splunkCheckConnection() {
        log.info("Checking Splunk connection");
        try {
            JsonNode result = splunkClient.checkConnection();
            return toJson(result);
        } catch (Exception e) {
            log.error("Splunk connection check failed: {}", e.getMessage(), e);
            return toJson(Map.of("connected", false, "error", e.getMessage()));
        }
    }

    @Tool(description = """
        Get the Splunk index name for a given environment.
        Maps environment names (uat, prod) to their configured Splunk index names.
        Use this before querying to get the correct index for the target environment.
        """)
    public String splunkGetIndexForEnvironment(
            @ToolParam(description = "The environment name: uat or prod") String env) {
        log.info("Getting Splunk index for environment: {}", env);
        Map<String, String> indexMap = splunkClient.getEnvironmentIndexMap();
        String index = indexMap.get(env.toLowerCase());
        if (index != null) {
            return toJson(Map.of("environment", env, "index", index));
        } else {
            return toJson(Map.of("error", "Unknown environment: " + env + ". Valid: " + indexMap.keySet()));
        }
    }

    @Tool(description = """
        Execute a Splunk SPL query and return results with an analysis summary.
        The query is run against the Splunk search API. For index queries like 'index=myindex error',
        the 'search' command prefix is auto-added.
        Results include all fetched events plus an auto-generated analysis summary with:
        - Error/severity distribution
        - Timeline (first/last event)
        - Top hosts, sources, sourcetypes
        - Unique error messages with occurrence counts
        If results are truncated, guidance to narrow the query is included.
        """)
    public String splunkExecuteQuery(
            @ToolParam(description = "The SPL query to execute") String query,
            @ToolParam(description = "Earliest time for the search (e.g. -1h, -7d, 2024-01-01T00:00:00). Optional.") String earliestTime,
            @ToolParam(description = "Latest time for the search (e.g. now, -1h, 2024-01-02T00:00:00). Optional.") String latestTime,
            @ToolParam(description = "Maximum number of results to return. Optional, defaults to config max.") Integer maxResults) {
        log.info("Executing Splunk query: {}", query);
        try {
            int max = (maxResults != null && maxResults > 0) ? maxResults : splunkConfig.getQuery().getMaxResults();
            List<JsonNode> results = splunkClient.executeQuery(query, earliestTime, latestTime, max);
            boolean truncated = results.size() >= max;
            ObjectNode response = formatter.formatQueryResponse(results, max, truncated);
            return toJson(response);
        } catch (Exception e) {
            log.error("Splunk query execution failed: {}", e.getMessage(), e);
            return toJson(Map.of("error", "Query failed: " + e.getMessage()));
        }
    }

    @Tool(description = """
        List all available Splunk indexes with their event counts and sizes.
        Returns index name, total event count, current DB size in MB, and disabled status.
        """)
    public String splunkGetAvailableIndexes() {
        log.info("Getting available Splunk indexes");
        try {
            JsonNode indexes = splunkClient.getAvailableIndexes();
            return toJson(Map.of("indexes", indexes));
        } catch (Exception e) {
            log.error("Failed to get Splunk indexes: {}", e.getMessage(), e);
            return toJson(Map.of("error", "Failed to get indexes: " + e.getMessage()));
        }
    }

    @Tool(description = """
        List available sourcetypes in Splunk, optionally filtered by index.
        Returns sourcetype metadata from the last 24 hours.
        """)
    public String splunkGetSourcetypes(
            @ToolParam(description = "Optional index name to filter sourcetypes by") String index) {
        log.info("Getting Splunk sourcetypes for index: {}", index);
        try {
            List<JsonNode> sourcetypes = splunkClient.getSourcetypes(index);
            return toJson(Map.of("sourcetypes", sourcetypes, "count", sourcetypes.size()));
        } catch (Exception e) {
            log.error("Failed to get Splunk sourcetypes: {}", e.getMessage(), e);
            return toJson(Map.of("error", "Failed to get sourcetypes: " + e.getMessage()));
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
