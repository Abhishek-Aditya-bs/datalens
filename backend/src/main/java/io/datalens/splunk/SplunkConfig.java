package io.datalens.splunk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("splunk")
@ConfigurationProperties(prefix = "datalens.splunk")
public class SplunkConfig {

    private String host;
    private int port = 8089;
    private String username;
    private String password;
    private boolean verifySsl = false;
    private int timeout = 30;
    private Indexes indexes = new Indexes();
    private QuerySettings query = new QuerySettings();

    public static class Indexes {
        private String uat = "index_app_fxs_uat";
        private String prod = "index_app_fxs";

        public String getUat() { return uat; }
        public void setUat(String uat) { this.uat = uat; }
        public String getProd() { return prod; }
        public void setProd(String prod) { this.prod = prod; }
    }

    public static class QuerySettings {
        private String defaultEarliestTime = "-30d";
        private String defaultLatestTime = "now";
        private int maxResults = 10000;
        private int pageSize = 1000;
        private int maxExecutionTime = 300;

        public String getDefaultEarliestTime() { return defaultEarliestTime; }
        public void setDefaultEarliestTime(String defaultEarliestTime) { this.defaultEarliestTime = defaultEarliestTime; }
        public String getDefaultLatestTime() { return defaultLatestTime; }
        public void setDefaultLatestTime(String defaultLatestTime) { this.defaultLatestTime = defaultLatestTime; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public int getMaxExecutionTime() { return maxExecutionTime; }
        public void setMaxExecutionTime(int maxExecutionTime) { this.maxExecutionTime = maxExecutionTime; }
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isVerifySsl() { return verifySsl; }
    public void setVerifySsl(boolean verifySsl) { this.verifySsl = verifySsl; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
    public Indexes getIndexes() { return indexes; }
    public void setIndexes(Indexes indexes) { this.indexes = indexes; }
    public QuerySettings getQuery() { return query; }
    public void setQuery(QuerySettings query) { this.query = query; }
}
