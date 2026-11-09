package com.agentshelf.shared.internal.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The spec is committed at {@code docs/api/openapi.json}; the frontend client is
 * generated from it. A relative server URL keeps the spec the same on every machine.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  /** Spring's ProblemDetail: its fields are optional by RFC 9457, so they stay optional. */
  private static final Set<String> OPTIONAL_FIELD_SCHEMAS = Set.of("ProblemDetail");

  @Bean
  OpenAPI agentShelfOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("AgentShelf API")
                .version("v1")
                .description("Dashboard API for suppliers. Errors use RFC 9457 problem details."))
        .servers(List.of(new Server().url("/")));
  }

  /**
   * Our DTOs are {@code @NullMarked}: every field is non-null unless it says {@code @Nullable}.
   * Mark all their properties as required so the generated TypeScript types are not optional. A DTO
   * that gains a nullable field must list it here, and the contract test will show the change.
   */
  @Bean
  OpenApiCustomizer requiredByDefault() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
        Map<String, Schema> properties = entry.getValue().getProperties();
        if (!OPTIONAL_FIELD_SCHEMAS.contains(entry.getKey()) && properties != null) {
          entry
              .getValue()
              .setRequired(new ArrayList<>(properties.keySet().stream().sorted().toList()));
        }
      }
    };
  }
}
