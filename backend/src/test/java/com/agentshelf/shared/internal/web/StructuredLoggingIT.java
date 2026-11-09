package com.agentshelf.shared.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Uses the same JSON log format as the prod profile. */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorTestController.class})
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIT {

  @Autowired private MockMvcTester mvc;

  @Test
  void logLinesAreJsonWithRequestId(CapturedOutput output) {
    mvc.get().uri("/test/errors/boom").header(RequestIdFilter.HEADER, "log-test-0001").exchange();

    List<String> lines =
        output.getOut().lines().filter(line -> line.contains("Unhandled exception")).toList();
    assertThat(lines).hasSize(1);

    JsonNode json = JsonMapper.builder().build().readTree(lines.getFirst());
    assertThat(json.path("requestId").asString()).isEqualTo("log-test-0001");
    assertThat(json.path("log").path("level").asString()).isEqualTo("ERROR");
  }
}
