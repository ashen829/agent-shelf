package com.agentshelf.enrichment.internal.model;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Creates the Claude adapter only when AI cleanup is switched on. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnrichmentProperties.class)
class EnrichmentModelConfig {

  @Bean
  @ConditionalOnProperty(name = "agentshelf.enrichment.enabled", havingValue = "true")
  ProductEnrichmentModel claudeEnrichmentModel(EnrichmentProperties properties, JsonMapper json) {
    String key = properties.claudeKey();
    if (key == null || key.isBlank()) {
      throw new IllegalStateException(
          "AI cleanup is enabled but AGENTSHELF_CLAUDE_KEY is not set.");
    }
    AnthropicOkHttpClient.Builder client = AnthropicOkHttpClient.builder().apiKey(key);
    String baseUrl = properties.baseUrl();
    if (baseUrl != null && !baseUrl.isBlank()) {
      client.baseUrl(baseUrl);
    }
    AnthropicClient built = client.build();
    return new ClaudeBatchEnrichmentModel(built, properties.model(), json);
  }
}
