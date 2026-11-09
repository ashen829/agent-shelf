package com.agentshelf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void healthIsUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatus(HttpStatus.OK)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void livenessAndReadinessProbesAreUp() {
    assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatus(HttpStatus.OK);
    assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatus(HttpStatus.OK);
  }

  @Test
  void flywayCreatedTheBaselineSchema() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'identity'",
            Integer.class);
    assertThat(count).isEqualTo(2);
  }
}
