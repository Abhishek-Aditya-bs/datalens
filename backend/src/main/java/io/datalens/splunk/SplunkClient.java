package io.datalens.splunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile("splunk")
public class SplunkClient {

    private static final Logger log = LoggerFactory.getLogger(SplunkClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long SESSION_LIFETIME_MS = 3600_000; // 1 hour

    private final SplunkConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private String sessionToken;
    private Instant sessionCreatedAt;

    public SplunkClient(SplunkConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = buildHttpClient();
    }

    private HttpClient buildHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeout()));

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
        return "https://" + config.getHost() + ":" + config.getPort();
    }

    private synchronized String getSessionToken() throws Exception {
        if (sessionToken != null && sessionCreatedAt != null
                && Instant.now().toEpochMilli() - sessionCreatedAt.toEpochMilli() < SESSION_LIFETIME_MS) {
            return sessionToken;
        }

        String body = "username=" + URLEncoder.encode(config.getUsername(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(config.getPassword(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/services/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Splunk authentication failed: HTTP " + response.statusCode());
        }

        // Parse XML response for session key
        String responseBody = response.body();
        int start = responseBody.indexOf("<sessionKey>") + "<sessionKey>".length();
        int end = responseBody.indexOf("</sessionKey>");
        if (start < "<sessionKey>".length() || end < 0) {
            throw new RuntimeException("Failed to parse Splunk session key from response");
        }

        sessionToken = responseBody.substring(start, end);
        sessionCreatedAt = Instant.now();
        log.info("Obtained new Splunk session token");
        return sessionToken;
    }

    private HttpRequest.Builder authenticatedRequest(String path) throws Exception {
        return HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Authorization", "Splunk " + getSessionToken());
    }

    private String executeWithRetry(HttpRequest request) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 401) {
                    // Session expired, clear and retry
                    synchronized (this) {
                        sessionToken = null;
                        sessionCreatedAt = null;
                    }
                    if (attempt < MAX_RETRIES) {
                        // Rebuild request with new token
                        request = HttpRequest.newBuilder(request, (k, v) -> true)
                                .header("Authorization", "Splunk " + getSessionToken())
                                .build();
                        continue;
                    }
                }
                if (response.statusCode() >= 400) {
                    throw new RuntimeException("Splunk API error: HTTP " + response.statusCode() + " - " + response.body());
                }
                return response.body();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("Splunk request attempt {} failed, retrying in {}ms: {}", attempt, backoffMs, e.getMessage());
                    Thread.sleep(backoffMs);
                }
            }
        }
        throw new RuntimeException("Splunk request failed after " + MAX_RETRIES + " attempts", lastException);
    }

    public JsonNode checkConnection() throws Exception {
        String response = executeWithRetry(
                authenticatedRequest("/services/server/info?output_mode=json").GET().build()
        );
        JsonNode root = objectMapper.readTree(response);
        JsonNode entry = root.path("entry").path(0).path("content");
        return objectMapper.createObjectNode()
                .put("connected", true)
                .put("serverName", entry.path("serverName").asText())
                .put("version", entry.path("version").asText())
                .put("os", entry.path("os_name").asText())
                .put("host", config.getHost())
                .put("port", config.getPort());
    }

    public List<JsonNode> executeQuery(String query, String earliestTime, String latestTime, int maxResults) throws Exception {
        // Auto-prepend "search" for index= queries
        String spl = query.trim();
        if (spl.startsWith("index=") || spl.startsWith("index =")) {
            spl = "search " + spl;
        }

        String et = (earliestTime != null && !earliestTime.isBlank()) ? earliestTime : config.getQuery().getDefaultEarliestTime();
        String lt = (latestTime != null && !latestTime.isBlank()) ? latestTime : config.getQuery().getDefaultLatestTime();
        int cap = maxResults > 0 ? Math.min(maxResults, config.getQuery().getMaxResults()) : config.getQuery().getMaxResults();

        // Create search job
        String jobBody = "search=" + URLEncoder.encode(spl, StandardCharsets.UTF_8)
                + "&earliest_time=" + URLEncoder.encode(et, StandardCharsets.UTF_8)
                + "&latest_time=" + URLEncoder.encode(lt, StandardCharsets.UTF_8)
                + "&max_count=" + cap
                + "&output_mode=json";

        String jobResponse = executeWithRetry(
                authenticatedRequest("/services/search/jobs")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(jobBody))
                        .build()
        );

        String sid = objectMapper.readTree(jobResponse).path("sid").asText();
        if (sid == null || sid.isEmpty()) {
            throw new RuntimeException("Failed to get search job SID from Splunk");
        }

        log.info("Created Splunk search job: {}", sid);

        // Poll until done
        long startTime = System.currentTimeMillis();
        long maxWaitMs = config.getQuery().getMaxExecutionTime() * 1000L;

        while (true) {
            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                throw new RuntimeException("Splunk query timed out after " + config.getQuery().getMaxExecutionTime() + "s");
            }

            String statusResponse = executeWithRetry(
                    authenticatedRequest("/services/search/jobs/" + sid + "?output_mode=json").GET().build()
            );
            JsonNode statusNode = objectMapper.readTree(statusResponse);
            JsonNode content = statusNode.path("entry").path(0).path("content");

            if (content.path("isDone").asBoolean(false)) {
                break;
            }

            Thread.sleep(1000);
        }

        // Fetch results with pagination
        List<JsonNode> allResults = new ArrayList<>();
        int pageSize = config.getQuery().getPageSize();
        int offset = 0;

        while (allResults.size() < cap) {
            String resultsResponse = executeWithRetry(
                    authenticatedRequest("/services/search/jobs/" + sid + "/results?output_mode=json&count=" + pageSize + "&offset=" + offset)
                            .GET().build()
            );

            JsonNode resultsNode = objectMapper.readTree(resultsResponse);
            JsonNode results = resultsNode.path("results");

            if (!results.isArray() || results.isEmpty()) {
                break;
            }

            for (JsonNode result : results) {
                allResults.add(result);
            }

            if (results.size() < pageSize) {
                break; // Last page
            }

            offset += pageSize;
        }

        log.info("Fetched {} results for search job {}", allResults.size(), sid);
        return allResults;
    }

    public JsonNode getAvailableIndexes() throws Exception {
        String response = executeWithRetry(
                authenticatedRequest("/services/data/indexes?output_mode=json&count=0").GET().build()
        );
        JsonNode root = objectMapper.readTree(response);
        JsonNode entries = root.path("entry");

        var arrayNode = objectMapper.createArrayNode();
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                String name = entry.path("name").asText();
                JsonNode content = entry.path("content");
                arrayNode.add(objectMapper.createObjectNode()
                        .put("name", name)
                        .put("totalEventCount", content.path("totalEventCount").asText())
                        .put("currentDBSizeMB", content.path("currentDBSizeMB").asText())
                        .put("disabled", content.path("disabled").asBoolean()));
            }
        }
        return arrayNode;
    }

    public List<JsonNode> getSourcetypes(String index) throws Exception {
        String spl = "| metadata type=sourcetypes";
        if (index != null && !index.isBlank()) {
            spl += " index=" + index;
        }
        return executeQuery(spl, "-24h", "now", 1000);
    }

    public Map<String, String> getEnvironmentIndexMap() {
        return Map.of(
                "uat", config.getIndexes().getUat(),
                "prod", config.getIndexes().getProd()
        );
    }
}
