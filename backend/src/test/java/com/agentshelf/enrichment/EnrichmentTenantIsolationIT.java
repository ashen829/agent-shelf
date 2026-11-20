package com.agentshelf.enrichment;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.product;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.Eventually;
import com.agentshelf.TestcontainersConfiguration;
import com.agentshelf.catalog.CatalogApi;
import com.agentshelf.enrichment.internal.application.EnrichmentRunner;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
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

/** One supplier can never see another supplier's AI jobs, suggestions or products. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "agentshelf.enrichment.enabled=true",
      "agentshelf.enrichment.claude-key=not-used-in-tests"
    })
@Import({TestcontainersConfiguration.class, FakeEnrichmentModel.Config.class})
class EnrichmentTenantIsolationIT {

  @LocalServerPort private int port;
  @Autowired private FakeEnrichmentModel model;
  @Autowired private EnrichmentRunner runner;
  @Autowired private CatalogApi catalog;
  @Autowired private JdbcTemplate jdbc;
  private BrowserClient supplierA;
  private BrowserClient supplierB;
  private String jobOfA;
  private UUID productOfA;

  @BeforeEach
  void supplierAHasSuggestions() {
    supplierA = signedInSupplier(port, "USD");
    supplierB = signedInSupplier(port, "EUR");
    productOfA =
        UUID.fromString(
            json(supplierA.post("/api/v1/products", product("A-1", "10", "PIPE")))
                .path("id")
                .asString());
    jobOfA = json(supplierA.post("/api/v1/enrichment/jobs", "{}")).path("id").asString();
    Eventually.until(
        () -> json(supplierA.get("/api/v1/enrichment/jobs/" + jobOfA)),
        job -> job.path("status").asString().equals("SUBMITTED"),
        Duration.ofSeconds(20));
    String batchId =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "select batch_id from enrichment.enrichment_job where id = ?",
                String.class,
                UUID.fromString(jobOfA)));
    model.finish(
        batchId, p -> List.of(new ProposedChange("name", "Pipe", "Case.", 0.9)), TokenUsage.NONE);
    runner.run(UUID.fromString(jobOfA), 0);
  }

  @Test
  void anotherSuppliersJobLooksLikeItDoesNotExist() {
    String path = "/api/v1/enrichment/jobs/" + jobOfA;

    assertThat(supplierB.get(path).statusCode()).isEqualTo(404);
    assertThat(supplierB.get(path + "/suggestions").statusCode()).isEqualTo(404);
    assertThat(json(supplierB.get("/api/v1/enrichment/jobs")).path("totalItems").asLong()).isZero();
    assertThat(json(supplierB.get("/api/v1/enrichment")).path("latestJob").isNull()).isTrue();

    assertThat(json(supplierA.get(path + "/suggestions")).path("totalItems").asLong()).isOne();
  }

  @Test
  void anotherSuppliersJobDoesNotBlockStarting() {
    assertThat(supplierB.post("/api/v1/enrichment/jobs", "{}").statusCode()).isEqualTo(202);
  }

  @Test
  void catalogReadsForEnrichmentAreScopedBySupplier() {
    UUID supplierOfB = UUID.randomUUID();

    assertThat(catalog.products(supplierOfB, List.of(productOfA))).isEmpty();
    assertThat(catalog.activeProducts(supplierOfB, 10)).isEmpty();
    assertThat(catalog.categoryNames(supplierOfB, 10)).isEmpty();
  }
}
