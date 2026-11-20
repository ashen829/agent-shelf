package com.agentshelf.enrichment.internal.model;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI cleanup settings ({@code agentshelf.enrichment.*}). Off by default; turning it on needs the
 * Claude key in {@code AGENTSHELF_CLAUDE_KEY}.
 *
 * @param enabled feature flag: off until the review screen ships
 * @param claudeKey the Claude API key; never logged
 * @param model the Claude model for cleanup (CLAUDE.md: Haiku, through the Batches API)
 * @param baseUrl only for tests against a fake API; empty means the real API
 * @param maxProducts most products sent in one job
 */
@ConfigurationProperties("agentshelf.enrichment")
public record EnrichmentProperties(
    @DefaultValue("false") boolean enabled,
    @Nullable String claudeKey,
    @DefaultValue("claude-haiku-5-5") String model,
    @Nullable String baseUrl,
    @DefaultValue("5000") int maxProducts) {

  @Override
  public String toString() {
    // Keeps the key out of logs and error messages.
    return "EnrichmentProperties[enabled=%s, model=%s, maxProducts=%d]"
        .formatted(enabled, model, maxProducts);
  }
}
