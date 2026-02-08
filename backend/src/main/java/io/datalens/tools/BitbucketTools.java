package io.datalens.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datalens.bitbucket.BitbucketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("bitbucket")
public class BitbucketTools {

    private static final Logger log = LoggerFactory.getLogger(BitbucketTools.class);

    private final BitbucketClient bitbucketClient;
    private final ObjectMapper objectMapper;

    public BitbucketTools(BitbucketClient bitbucketClient, ObjectMapper objectMapper) {
        this.bitbucketClient = bitbucketClient;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Check the connection to Bitbucket Data Center and return server information. "
            + "Returns server version and display name. Use this to verify Bitbucket is accessible.")
    public String bitbucketCheckConnection() {
        log.info("Checking Bitbucket connection");
        try {
            JsonNode result = bitbucketClient.checkConnection();
            return toJson(result);
        } catch (Exception e) {
            log.error("Bitbucket connection check failed: {}", e.getMessage(), e);
            return toJson(Map.of("connected", false, "error", e.getMessage()));
        }
    }

    @Tool(description = "Search for code or files across all repositories in Bitbucket Data Center. "
            + "Searches the default branch of all repos in the configured project. "
            + "Use this when Splunk logs reference a source file and you need to find which repository contains it "
            + "and what its full path is. Returns matching file paths, repository names, and matching line content. "
            + "After finding results, use bitbucketReadFile with the repo slug and file path from the results. "
            + "Example queries: 'RaidServiceImpl.java', 'NullPointerException lang:java', 'class ErrorHandler'")
    public String bitbucketSearchCode(
            @ToolParam(description = "Search query - file name, class name, or code keyword") String query,
            @ToolParam(description = "Max results to return. Optional, defaults to 25.") Integer maxResults) {
        log.info("Searching Bitbucket code: query='{}', maxResults={}", query, maxResults);
        try {
            int limit = (maxResults != null && maxResults > 0) ? maxResults : 25;
            JsonNode result = bitbucketClient.searchCode(query, limit);
            return toJson(result);
        } catch (Exception e) {
            log.error("Bitbucket code search failed: {}", e.getMessage(), e);
            return toJson(Map.of("error", "Code search failed: " + e.getMessage()));
        }
    }

    @Tool(description = "Read the full content of a source file from a Bitbucket repository. "
            + "Use this after bitbucketSearchCode to read the actual code of a file. "
            + "Pass the repository slug and file path exactly as returned by bitbucketSearchCode. "
            + "Returns the raw file content as text.")
    public String bitbucketReadFile(
            @ToolParam(description = "The repository slug (e.g. raid-service) — use the value from bitbucketSearchCode results") String repoSlug,
            @ToolParam(description = "Full file path in the repo (e.g. src/main/java/com/jpmc/raid/RaidServiceImpl.java) — use the value from bitbucketSearchCode results") String filePath,
            @ToolParam(description = "Branch or commit to read from. Optional, defaults to default branch.") String branch) {
        log.info("Reading file from Bitbucket: repo='{}', path='{}', branch='{}'", repoSlug, filePath, branch);
        try {
            String content = bitbucketClient.getFileContent(repoSlug, filePath, branch);
            return toJson(Map.of(
                    "repository", repoSlug,
                    "filePath", filePath,
                    "content", content
            ));
        } catch (Exception e) {
            log.error("Bitbucket file read failed: {}", e.getMessage(), e);
            return toJson(Map.of("error", "File read failed: " + e.getMessage()));
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
