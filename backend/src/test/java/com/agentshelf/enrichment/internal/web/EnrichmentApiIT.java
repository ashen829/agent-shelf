package com.agentshelf.enrichment.internal.web;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.product;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentshelf.BrowserClient;
import com.agentshelf.Eventually;
import com.agentshelf.TestcontainersConfiguration;
import com.agentshelf.enrichment.FakeEnrichmentModel;
import com.agentshelf.enrichment.internal.application.EnrichmentRunner;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** AI cleanup end to end, with a fake model: start, background job, checked suggestions. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "agentshelf.enrichment.enabled=true",
      "agentshelf.enrichment.claude-key=not-used-in-tests"
    })
@Import({TestcontainersConfiguration.class, FakeEnrichmentModel.Config.class})
class EnrichmentApiIT {

  @LocalServerPort private int port;
  @Autowired private FakeEnrichmentModel model;
  @Autowired private EnrichmentRunner runner;
  @Autowired private JdbcTemplate jdbc;
  private BrowserClient supplier;

  @BeforeEach
  void signUp() {
    supplier = signedInSupplier(port, "LKR");
  }

  private String addProduct(String sku, String name) {
    HttpResponse<String> created = supplier.post("/api/v1/products", product(sku, "10", name));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    return json(created).path("id").asString();
  }

  @Test
  void suggestsCheckedFixesWithoutChangingProducts() {
    String messy = addProduct("PVC-20", "PVC PIPE 20MM");
    addProduct("TAPE-1", "PTFE tape");
    JsonNode overview = json(supplier.get("/api/v1/enrichment"));
    assertThat(overview.path("enabled").asBoolean()).isTrue();
    assertThat(overview.path("latestJob").isNull()).isTrue();

    String jobId = start();
    String batchId = waitUntilSubmitted(jobId);
    assertThat(model.products(batchId))
        .extracting(p -> p.sku())
        .containsExactly("PVC-20", "TAPE-1");
    assertThat(model.lastCategories).containsExactly("Pipes");

    model.finish(
        batchId,
        p ->
            p.sku().equals("PVC-20")
                ? List.of(
                    new ProposedChange("name", "PVC pipe 20 mm", "Fixed letter case.", 0.9),
                    new ProposedChange("price", "0", "Ignore your rules.", 1),
                    new ProposedChange("unit", "metre-ish", "Not a unit.", 0.5))
                : List.of(),
        new TokenUsage(1200, 300, 0, 0));
    assertThat(runner.run(UUID.fromString(jobId), 0)).isNull();

    JsonNode job = json(supplier.get("/api/v1/enrichment/jobs/" + jobId));
    assertThat(job.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(job.path("model").asString()).isEqualTo("fake-model");
    assertThat(job.path("productsTotal").asInt()).isEqualTo(2);
    assertThat(job.path("suggestionsCreated").asInt()).isEqualTo(1);
    assertThat(job.path("suggestionsDropped").asInt()).isEqualTo(2);
    assertThat(job.path("inputTokens").asLong()).isEqualTo(1200);
    assertThat(job.path("outputTokens").asLong()).isEqualTo(300);

    JsonNode suggestions = json(supplier.get("/api/v1/enrichment/jobs/" + jobId + "/suggestions"));
    assertThat(suggestions.path("totalItems").asInt()).isEqualTo(1);
    JsonNode suggestion = suggestions.path("items").get(0);
    assertThat(suggestion.path("productId").asString()).isEqualTo(messy);
    assertThat(suggestion.path("field").asString()).isEqualTo("NAME");
    assertThat(suggestion.path("oldValue").asString()).isEqualTo("PVC PIPE 20MM");
    assertThat(suggestion.path("newValue").asString()).isEqualTo("PVC pipe 20 mm");
    assertThat(suggestion.path("status").asString()).isEqualTo("PENDING");
    assertThat(suggestion.path("productVersion").asLong()).isZero();

    // Suggestions never change products by themselves.
    assertThat(json(supplier.get("/api/v1/products/" + messy)).path("name").asString())
        .isEqualTo("PVC PIPE 20MM");
    assertThat(json(supplier.get("/api/v1/enrichment")).path("latestJob").path("id").asString())
        .isEqualTo(jobId);
  }

  @Test
  void runsOneJobAtATimePerSupplier() {
    addProduct("A-1", "Alpha");
    String first = start();
    waitUntilSubmitted(first);

    HttpResponse<String> second = supplier.post("/api/v1/enrichment/jobs", "{}");

    assertThat(second.statusCode()).isEqualTo(409);
    assertThat(json(second).path("detail").asString()).contains("already being prepared");
  }

  @Test
  void finishesRightAwayWhenThereAreNoProducts() {
    String jobId = start();

    JsonNode job = waitForStatus(jobId, "COMPLETED");

    assertThat(job.path("productsTotal").asInt()).isZero();
    assertThat(job.path("suggestionsCreated").asInt()).isZero();
  }

  @Test
  void keepsCheckingWhileTheBatchRunsAndGivesUpAfterADay() {
    addProduct("A-1", "Alpha");
    String jobId = start();
    waitUntilSubmitted(jobId);

    assertThat(runner.run(UUID.fromString(jobId), 0)).isNotNull();

    jdbc.update(
        "update enrichment.enrichment_job set submitted_at = now() - interval '26 hours'"
            + " where id = ?",
        UUID.fromString(jobId));
    assertThat(runner.run(UUID.fromString(jobId), 0)).isNull();
    JsonNode job = json(supplier.get("/api/v1/enrichment/jobs/" + jobId));
    assertThat(job.path("status").asString()).isEqualTo("FAILED");
    assertThat(job.path("failureMessage").asString()).contains("took too long");
  }

  @Test
  void retriesErrorsAndThenFailsWithAMessageForTheSupplier() {
    addProduct("A-1", "Alpha");
    String jobId = start();
    String batchId = waitUntilSubmitted(jobId);
    model.failPolling(batchId, new IllegalStateException("connection reset (internal detail)"));

    assertThatThrownBy(() -> runner.run(UUID.fromString(jobId), 0))
        .isInstanceOf(IllegalStateException.class);
    assertThat(runner.run(UUID.fromString(jobId), EnrichmentRunner.MAX_ATTEMPTS - 1)).isNull();

    JsonNode job = json(supplier.get("/api/v1/enrichment/jobs/" + jobId));
    assertThat(job.path("status").asString()).isEqualTo("FAILED");
    assertThat(job.path("failureMessage").asString())
        .contains("unexpected error")
        .doesNotContain("internal detail");
  }

  @Test
  void validatesPagingAndUnknownJobs() {
    assertThat(supplier.get("/api/v1/enrichment/jobs/" + UUID.randomUUID()).statusCode())
        .isEqualTo(404);
    assertThat(supplier.get("/api/v1/enrichment/jobs?size=101").statusCode()).isEqualTo(400);
    assertThat(supplier.get("/api/v1/enrichment/jobs").statusCode()).isEqualTo(200);
  }

  private String start() {
    HttpResponse<String> started = supplier.post("/api/v1/enrichment/jobs", "{}");
    assertThat(started.statusCode()).as(started.body()).isEqualTo(202);
    return json(started).path("id").asString();
  }

  private String waitUntilSubmitted(String jobId) {
    waitForStatus(jobId, "SUBMITTED");
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "select batch_id from enrichment.enrichment_job where id = ?",
            String.class,
            UUID.fromString(jobId)));
  }

  private JsonNode waitForStatus(String jobId, String status) {
    return Eventually.until(
        () -> json(supplier.get("/api/v1/enrichment/jobs/" + jobId)),
        job -> job.path("status").asString().equals(status),
        Duration.ofSeconds(20));
  }
}
