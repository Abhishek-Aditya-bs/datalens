package io.datalens.bitbucket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Component
@Profile("bitbucket")
public class BitbucketClient {

    private static final Logger log = LoggerFactory.getLogger(BitbucketClient.class);
    private static final int MAX_RETRIES = 3;

    private final BitbucketConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BitbucketClient(BitbucketConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getConnectTimeout()));

        if (!config.isVerifySsl()) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }}, new java.security.SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                log.warn("Failed to configure trust-all SSL context, using default", e);
            }
        }

        return builder.build();
    }

    private String getBaseUrl() {
        String url = config.getBaseUrl();
        // Strip trailing slash
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private HttpRequest.Builder authenticatedRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Authorization", "Bearer " + config.getToken())
                .timeout(Duration.ofSeconds(config.getReadTimeout()));
    }

    private String executeWithRetry(HttpRequest request) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new RuntimeException("Bitbucket API error: HTTP " + response.statusCode() + " - " + response.body());
                }
                return response.body();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("Bitbucket request attempt {} failed, retrying in {}ms: {}", attempt, backoffMs, e.getMessage());
                    Thread.sleep(backoffMs);
                }
            }
        }
        throw new RuntimeException("Bitbucket request failed after " + MAX_RETRIES + " attempts", lastException);
    }

    public JsonNode checkConnection() throws Exception {
        String response = executeWithRetry(
                authenticatedRequest("/rest/api/1.0/application-properties").GET().build()
        );
        JsonNode root = objectMapper.readTree(response);
        return objectMapper.createObjectNode()
                .put("connected", true)
                .put("version", root.path("version").asText())
                .put("displayName", root.path("displayName").asText())
                .put("buildNumber", root.path("buildNumber").asText());
    }

    public JsonNode searchCode(String query, int limit) throws Exception {
        int effectiveLimit = limit > 0 ? Math.min(limit, 999) : 25;

        String fullQuery = query + " project:" + config.getDefaultProject();

        ObjectNode bodyNode = objectMapper.createObjectNode();
        bodyNode.put("query", fullQuery);
        ObjectNode entities = bodyNode.putObject("entities");
        ObjectNode code = entities.putObject("code");
        code.put("start", 0);
        code.put("limit", effectiveLimit);

        String jsonBody = objectMapper.writeValueAsString(bodyNode);

        log.info("Searching Bitbucket code: query='{}', limit={}", fullQuery, effectiveLimit);

        String response = executeWithRetry(
                authenticatedRequest("/rest/search/latest/search")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build()
        );

        JsonNode root = objectMapper.readTree(response);
        JsonNode codeResults = root.path("code");
        JsonNode values = codeResults.path("values");

        ArrayNode results = objectMapper.createArrayNode();
        if (values.isArray()) {
            for (JsonNode value : values) {
                ObjectNode match = objectMapper.createObjectNode();
                match.put("repository", value.path("repository").path("slug").asText());
                match.put("project", value.path("repository").path("project").path("key").asText());
                match.put("filePath", value.path("file").path("path").asText());

                ArrayNode matchingLines = objectMapper.createArrayNode();
                JsonNode hitContexts = value.path("hitContexts");
                if (hitContexts.isArray()) {
                    for (JsonNode context : hitContexts) {
                        JsonNode lines = context.path("lines");
                        if (lines.isArray()) {
                            for (JsonNode line : lines) {
                                matchingLines.add(objectMapper.createObjectNode()
                                        .put("line", line.path("line").asInt())
                                        .put("text", line.path("text").asText()));
                            }
                        }
                    }
                }
                match.set("matchingLines", matchingLines);
                results.add(match);
            }
        }

        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("count", results.size());
        resultNode.put("query", fullQuery);
        resultNode.set("results", results);
        return resultNode;
    }

    public String getFileContent(String repoSlug, String filePath, String branch) throws Exception {
        String path = "/rest/api/1.0/projects/" + config.getDefaultProject()
                + "/repos/" + repoSlug
                + "/raw/" + filePath;

        if (branch != null && !branch.isBlank()) {
            path += "?at=" + branch;
        }

        log.info("Reading file from Bitbucket: repo={}, path={}, branch={}", repoSlug, filePath, branch);

        return executeWithRetry(
                authenticatedRequest(path).GET().build()
        );
    }
}
