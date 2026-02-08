package io.datalens.bitbucket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("bitbucket")
@ConfigurationProperties(prefix = "datalens.bitbucket")
public class BitbucketConfig {

    private String baseUrl;
    private String token;
    private String defaultProject;
    private boolean verifySsl = true;
    private int connectTimeout = 10;
    private int readTimeout = 30;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getDefaultProject() { return defaultProject; }
    public void setDefaultProject(String defaultProject) { this.defaultProject = defaultProject; }
    public boolean isVerifySsl() { return verifySsl; }
    public void setVerifySsl(boolean verifySsl) { this.verifySsl = verifySsl; }
    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
}
