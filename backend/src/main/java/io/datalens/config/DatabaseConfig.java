package io.datalens.config;

import io.datalens.datasource.model.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Properties;

/**
 * Oracle database configuration with Kerberos authentication support.
 * Activated when datalens.datasource.mode=oracle
 */
@Configuration
@ConfigurationProperties(prefix = "datalens.oracle")
@ConditionalOnProperty(name = "datalens.datasource.mode", havingValue = "oracle")
public class DatabaseConfig {

    private KerberosConfig kerberos = new KerberosConfig();
    private Map<String, EnvironmentConfig> environments;
    private int queryTimeoutSeconds = 60;

    public static class KerberosConfig {
        private String configPath;
        private String ccachePath;

        public String getConfigPath() { return configPath; }
        public void setConfigPath(String configPath) { this.configPath = configPath; }
        public String getCcachePath() { return ccachePath; }
        public void setCcachePath(String ccachePath) { this.ccachePath = ccachePath; }
    }

    public static class EnvironmentConfig {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    @PostConstruct
    public void init() {
        if (kerberos.getConfigPath() != null) {
            System.setProperty("java.security.krb5.conf", kerberos.getConfigPath());
        }
    }

    public String getDatabaseUrl(Environment environment) {
        String envKey = environment.name().toLowerCase();
        EnvironmentConfig config = environments.get(envKey);
        if (config == null) {
            throw new IllegalArgumentException("No database configuration for environment: " + environment);
        }
        return config.getUrl();
    }

    public Properties getConnectionProperties(Environment environment) {
        Properties props = new Properties();
        props.setProperty("oracle.net.authentication_services", "(KERBEROS5)");
        props.setProperty("oracle.net.kerberos5_cc_name", getKerberosCCachePath());
        props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(queryTimeoutSeconds * 1000));
        props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(queryTimeoutSeconds * 1000));
        return props;
    }

    private String getKerberosCCachePath() {
        if (kerberos.getCcachePath() != null) {
            return kerberos.getCcachePath();
        }
        return System.getenv("KRB5CCNAME") != null
                ? System.getenv("KRB5CCNAME")
                : "/tmp/krb5cc_" + System.getProperty("user.name");
    }

    // Getters and Setters
    public KerberosConfig getKerberos() { return kerberos; }
    public void setKerberos(KerberosConfig kerberos) { this.kerberos = kerberos; }
    public Map<String, EnvironmentConfig> getEnvironments() { return environments; }
    public void setEnvironments(Map<String, EnvironmentConfig> environments) { this.environments = environments; }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
}
