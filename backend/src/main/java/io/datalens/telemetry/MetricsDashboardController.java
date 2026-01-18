package io.datalens.telemetry;

import io.datalens.config.ChatMemoryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

/**
 * Custom metrics dashboard endpoint for DataLens.
 */
@RestController
@RequestMapping("/actuator")
public class MetricsDashboardController {

    private static final Logger log = LoggerFactory.getLogger(MetricsDashboardController.class);

    private final MeterRegistry meterRegistry;
    private final TelemetryService telemetryService;

    @Autowired(required = false)
    private ChatMemoryConfig.BoundedInMemoryChatMemory memoryRepository;

    public MetricsDashboardController(MeterRegistry meterRegistry, TelemetryService telemetryService) {
        this.meterRegistry = meterRegistry;
        this.telemetryService = telemetryService;
    }

    @GetMapping(value = "/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> dashboardJson() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("timestamp", Instant.now().toString());
        dashboard.put("application", "DataLens");
        dashboard.put("version", "1.0.0");

        int activeSessions = memoryRepository != null ? memoryRepository.getActiveSessionCount() : 0;
        dashboard.put("sessions", Map.of(
                "active", activeSessions,
                "maxAllowed", 1000
        ));

        dashboard.put("requests", Map.of(
                "total", getMetricValue("datalens.chat.requests"),
                "byEnvironment", getMetricsByTag("datalens.chat.requests", "environment"),
                "byTool", getMetricsByTag("datalens.chat.requests", "tool")
        ));

        dashboard.put("performance", Map.of(
                "avgQueryDurationMs", getTimerMean("datalens.db.query.duration"),
                "maxQueryDurationMs", getTimerMax("datalens.db.query.duration")
        ));

        dashboard.put("llm", Map.of(
                "promptTokens", getMetricValueByTag("datalens.llm.tokens", "type", "prompt"),
                "completionTokens", getMetricValueByTag("datalens.llm.tokens", "type", "completion")
        ));

        dashboard.put("jvm", Map.of(
                "heapUsedMb", getMetricValue("jvm.memory.used") / 1_000_000.0,
                "heapMaxMb", getMetricValue("jvm.memory.max") / 1_000_000.0
        ));

        dashboard.put("errors", Map.of(
                "total", getMetricValue("datalens.errors")
        ));

        return dashboard;
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    public String dashboardHtml() {
        Map<String, Object> data = dashboardJson();
        return buildHtmlDashboard(data);
    }

    @SuppressWarnings("unchecked")
    private String buildHtmlDashboard(Map<String, Object> data) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>DataLens Metrics Dashboard</title>
                <style>
                    * { box-sizing: border-box; }
                    body { font-family: -apple-system, sans-serif; padding: 24px; background: #f5f7fa; }
                    h1 { color: #1a1a2e; font-size: 28px; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; margin-bottom: 24px; }
                    .card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
                    .card h3 { color: #666; font-size: 12px; font-weight: 600; text-transform: uppercase; }
                    .metric { font-size: 32px; font-weight: 700; color: #2563eb; }
                    table { width: 100%%; border-collapse: collapse; }
                    th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #eee; }
                    th { background: #f8f9fa; font-weight: 600; font-size: 12px; text-transform: uppercase; }
                </style>
                <meta http-equiv="refresh" content="30">
            </head>
            <body>
                <h1>DataLens Metrics Dashboard</h1>
                <p>Last updated: %s</p>
                <div class="grid">
                    <div class="card"><h3>Active Sessions</h3><div class="metric">%d</div></div>
                    <div class="card"><h3>Total Requests</h3><div class="metric">%.0f</div></div>
                    <div class="card"><h3>Avg Query Time</h3><div class="metric">%.0f ms</div></div>
                    <div class="card"><h3>JVM Heap</h3><div class="metric">%.0f MB</div></div>
                </div>
            </body>
            </html>
            """.formatted(
                data.get("timestamp"),
                ((Map<?, ?>) data.get("sessions")).get("active"),
                ((Map<?, ?>) data.get("requests")).get("total"),
                ((Map<?, ?>) data.get("performance")).get("avgQueryDurationMs"),
                ((Map<?, ?>) data.get("jvm")).get("heapUsedMb")
        );
    }

    private double getMetricValue(String metricName) {
        try {
            var meters = meterRegistry.find(metricName).meters();
            if (meters.isEmpty()) return 0.0;
            return meters.stream()
                    .mapToDouble(m -> {
                        if (m instanceof io.micrometer.core.instrument.Counter c) {
                            return c.count();
                        }
                        return 0.0;
                    })
                    .sum();
        } catch (Exception e) {
            log.debug("Could not get metric {}: {}", metricName, e.getMessage());
            return 0.0;
        }
    }

    private double getMetricValueByTag(String metricName, String tagKey, String tagValue) {
        try {
            var counter = meterRegistry.find(metricName).tag(tagKey, tagValue).counter();
            return counter != null ? counter.count() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Map<String, Double> getMetricsByTag(String metricName, String tagKey) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            meterRegistry.find(metricName).meters().forEach(meter -> {
                meter.getId().getTags().stream()
                        .filter(tag -> tag.getKey().equals(tagKey))
                        .findFirst()
                        .ifPresent(tag -> {
                            double value = 0.0;
                            if (meter instanceof io.micrometer.core.instrument.Counter c) {
                                value = c.count();
                            }
                            result.merge(tag.getValue(), value, Double::sum);
                        });
            });
        } catch (Exception e) {
            log.debug("Could not get metrics by tag {}.{}: {}", metricName, tagKey, e.getMessage());
        }
        return result;
    }

    private double getTimerMean(String metricName) {
        try {
            var timer = meterRegistry.find(metricName).timer();
            return timer != null ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getTimerMax(String metricName) {
        try {
            var timer = meterRegistry.find(metricName).timer();
            return timer != null ? timer.max(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
