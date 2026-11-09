package com.agentshelf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the committed API contract ({@code docs/api/openapi.json}) in sync with the code. After an
 * intended API change, regenerate it with {@code ./mvnw verify -Dopenapi.write=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractIT {

  private static final Path SPEC = Path.of("..", "docs", "api", "openapi.json");
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Autowired private MockMvcTester mvc;

  @Test
  void committedSpecMatchesTheCode() throws IOException {
    String body = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
    JsonNode actual = JSON.readTree(body);

    if (Boolean.getBoolean("openapi.write")) {
      Files.createDirectories(SPEC.getParent());
      Files.writeString(SPEC, JSON.writeValueAsString(actual) + "\n");
    }

    assertThat(Files.exists(SPEC))
        .as("Missing %s. Run ./mvnw verify -Dopenapi.write=true", SPEC)
        .isTrue();
    assertThat(JSON.readTree(Files.readString(SPEC)))
        .as("docs/api/openapi.json is out of date. Run ./mvnw verify -Dopenapi.write=true")
        .isEqualTo(actual);
  }
}
