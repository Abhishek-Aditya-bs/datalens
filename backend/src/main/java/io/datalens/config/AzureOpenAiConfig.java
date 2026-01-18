package io.datalens.config;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientCertificateCredentialBuilder;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Azure OpenAI configuration using certificate authentication.
 * Activated when datalens.llm.provider=azure
 */
@Configuration
@ConditionalOnProperty(name = "datalens.llm.provider", havingValue = "azure")
public class AzureOpenAiConfig {

    @Value("${azure.openai.tenant-id}")
    private String tenantId;

    @Value("${azure.openai.client-id}")
    private String clientId;

    @Value("${azure.openai.certificate-path}")
    private String certificatePath;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.deployment-name:gpt-4}")
    private String deploymentName;

    /**
     * Creates TokenCredential using client certificate authentication.
     * This is used instead of API key for enterprise Azure OpenAI deployments.
     */
    @Bean
    public TokenCredential azureTokenCredential() {
        return new ClientCertificateCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .pemCertificate(certificatePath)
                .build();
    }

    /**
     * Custom chat options for Azure OpenAI.
     * Note: Do not set logprobs as GPT-5.1 doesn't support it.
     */
    @Bean
    @ConditionalOnProperty(name = "datalens.llm.provider", havingValue = "azure")
    public AzureOpenAiChatOptions azureOpenAiChatOptions() {
        return AzureOpenAiChatOptions.builder()
                .deploymentName(deploymentName)
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }
}
