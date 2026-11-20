package com.agentshelf.enrichment.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EnrichmentModelConfigTest {

  @Test
  void refusesToStartWithoutAKey() {
    EnrichmentProperties noKey = new EnrichmentProperties(true, " ", "claude-haiku-5-5", null, 10);

    assertThatThrownBy(() ->
            new EnrichmentModelConfig().claudeEnrichmentModel(noKey, JsonMapper.builder().build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AGENTSHELF_CLAUDE_KEY");
  }

  @Test
  void neverPrintsTheKey() {
    EnrichmentProperties properties =
        new EnrichmentProperties(true, "sk-secret-value", "claude-haiku-5-5", null, 10);

    assertThat(properties.toString()).doesNotContain("sk-secret-value");
  }
}
