package com.agentshelf.catalog.internal.web;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.Eventually;
import com.agentshelf.TestcontainersConfiguration;
import com.agentshelf.catalog.internal.importing.ImportRunner;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** CSV imports end to end: upload over HTTP, background job on PostgreSQL, results and errors. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImportApiIT {

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ImportRunner runner;
  private BrowserClient supplier;

  @BeforeEach
  void signUp() {
    supplier = signedInSupplier(port, "LKR");
  }

  @Test
  void importsFiveHundredRowsInTheBackground() {
    StringBuilder csv = new StringBuilder("SKU,Name,Category,Unit,Price,Stock\n");
    for (int i = 1; i <= 500; i++) {
      csv.append(
          "BOLT-%03d,Hex bolt M%d,Fasteners,pcs,%d.25,%d\n".formatted(i, i % 20 + 4, i, i * 10));
    }

    JsonNode upload = upload("bolts.csv", csv.toString());
    assertThat(upload.path("importJob").path("status").asString()).isEqualTo("UPLOADED");
    assertThat(upload.path("importJob").path("rowsTotal").asInt()).isEqualTo(500);
    assertThat(upload.path("preview").path("headers").size()).isEqualTo(6);
    assertThat(upload.path("preview").path("rows").size()).isEqualTo(5);
    JsonNode suggested = upload.path("preview").path("suggestedMapping");
    assertThat(suggested.path("sku").asInt()).isZero();
    assertThat(suggested.path("price").asInt()).isEqualTo(4);

    Instant started = Instant.now();
    JsonNode done = startAndWait(id(upload), suggested.toString(), ".", false);

    assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(done.path("rowsCreated").asInt()).isEqualTo(500);
    assertThat(done.path("rowsProcessed").asInt()).isEqualTo(500);
    assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(30));
    JsonNode products = json(supplier.get("/api/v1/products?size=1"));
    assertThat(products.path("totalItems").asLong()).isEqualTo(500);
    assertThat(products.path("items").get(0).path("price").path("currency").asString())
        .isEqualTo("LKR");
  }

  @Test
  void importsValidRowsAndReportsTheRestAsADownloadableFile() {
    String csv =
        """
        sku,name,price
        OK-1,Good pipe,10
        OK-2,Good tape,2.5
        BAD PRICE,=HYPERLINK("http://evil.example"),12.5O
        NO-NAME,,4
        OK-1,Same SKU again,11
        has space,Bad SKU,3
        """;

    JsonNode done = startAndWait(id(upload("mixed.csv", csv)), mapping(0, 1, 2), ".", false);

    assertThat(done.path("status").asString()).isEqualTo("COMPLETED_WITH_ERRORS");
    assertThat(done.path("rowsCreated").asInt()).isEqualTo(2);
    assertThat(done.path("rowsFailed").asInt()).isEqualTo(4);

    JsonNode errors =
        json(supplier.get("/api/v1/imports/" + done.path("id").asString() + "/errors"));
    assertThat(errors.path("items").valueStream().map(e -> e.path("rowNumber").asInt()))
        .containsExactly(4, 5, 6, 7);
    assertThat(errors.path("items").valueStream().map(e -> e.path("field").asString()))
        // Row 4 breaks two rules (space in SKU, bad price); the first one checked is reported.
        .containsExactly("price", "name", "sku", "sku");
    assertThat(errors.path("items").get(2).path("message").asString()).contains("already on row 2");

    HttpResponse<byte[]> file =
        supplier.download("/api/v1/imports/" + done.path("id").asString() + "/errors.csv");
    assertThat(file.headers().firstValue("Content-Type"))
        .hasValueSatisfying(t -> assertThat(t).startsWith("text/csv"));
    String report = new String(file.body(), StandardCharsets.UTF_8);
    assertThat(report).startsWith("﻿sku,name,price,Error");
    assertThat(report).contains("'=HYPERLINK").contains("Row 6: SKU OK-1 is already on row 2");
    assertThat(report.lines().count()).isEqualTo(5);
  }

  @Test
  void updatesProductsBySkuAndKeepsFieldsTheFileDoesNotHave() {
    supplier.post(
        "/api/v1/products",
        """
        {"sku": "PVC-20", "name": "PVC pipe", "description": "Grey, 6 m lengths",
         "price": "180", "stockQty": 40}
        """);

    JsonNode first =
        startAndWait(
            id(upload("p.csv", "sku,name,price\npvc-20,PVC pipe 20 mm,185.50\n")),
            mapping(0, 1, 2),
            ".",
            false);
    assertThat(first.path("rowsUpdated").asInt()).isEqualTo(1);

    JsonNode product = json(supplier.get("/api/v1/products")).path("items").get(0);
    assertThat(product.path("name").asString()).isEqualTo("PVC pipe 20 mm");
    assertThat(product.path("price").path("amount").asString()).isEqualTo("185.5000");
    assertThat(product.path("description").asString()).isEqualTo("Grey, 6 m lengths");
    assertThat(product.path("stockQty").asInt()).isEqualTo(40);

    JsonNode again =
        startAndWait(
            id(upload("p.csv", "sku,name,price\npvc-20,PVC pipe 20 mm,185.5\n")),
            mapping(0, 1, 2),
            ".",
            false);
    assertThat(again.path("rowsUnchanged").asInt()).isEqualTo(1);
  }

  @Test
  void readsSemicolonFilesWithDecimalCommas() {
    JsonNode done =
        startAndWait(
            id(upload("eu.csv", "Artikel;Bezeichnung;Preis\nA-1;Rohr;1.234,50\n")),
            mapping(0, 1, 2),
            ",",
            false);

    assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(
            json(supplier.get("/api/v1/products"))
                .path("items")
                .get(0)
                .path("price")
                .path("amount")
                .asString())
        .isEqualTo("1234.5000");
  }

  @Test
  void completeCatalogArchivesMissingProductsButNotRowsWithErrors() {
    for (String sku : new String[] {"KEEP", "TYPO", "GONE"}) {
      supplier.post(
          "/api/v1/products",
          "{\"sku\": \"%s\", \"name\": \"%s\", \"price\": \"1\", \"stockQty\": 1}"
              .formatted(sku, sku));
    }

    JsonNode done =
        startAndWait(
            id(upload("all.csv", "sku,name,price\nKEEP,Keep,1\nTYPO,Typo,1.2.3\n")),
            mapping(0, 1, 2),
            ".",
            true);

    assertThat(done.path("productsArchived").asInt()).isEqualTo(1);
    JsonNode active = json(supplier.get("/api/v1/products"));
    assertThat(active.path("items").valueStream().map(p -> p.path("sku").asString()))
        .containsExactlyInAnyOrder("KEEP", "TYPO");
    assertThat(
            json(supplier.get("/api/v1/products?status=ARCHIVED"))
                .path("items")
                .get(0)
                .path("sku")
                .asString())
        .isEqualTo("GONE");
  }

  @Test
  void onlyOneImportRunsAtATimePerSupplier() {
    String busy = id(upload("a.csv", "sku,name,price\nA,A,1\n"));
    String next = id(upload("b.csv", "sku,name,price\nB,B,1\n"));
    // Pretend the first one is still running (deterministic, unlike racing a real job).
    jdbc.update("UPDATE catalog.import_job SET status = 'RUNNING' WHERE id = ?::uuid", busy);

    HttpResponse<String> response = start(next, mapping(0, 1, 2), ".", false);

    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(json(response).path("detail").asString()).contains("still running");
  }

  @Test
  void runningAgainAfterACrashGivesTheSameProducts() {
    String id = id(upload("again.csv", "sku,name,price\nA,Alpha,1\nB,Beta,2\n"));
    startAndWait(id, mapping(0, 1, 2), ".", false);
    // Simulate a crash: the job is still marked RUNNING and the scheduler runs it again.
    jdbc.update("UPDATE catalog.import_job SET status = 'RUNNING' WHERE id = ?::uuid", id);

    runner.run(UUID.fromString(id), 0);

    JsonNode job = json(supplier.get("/api/v1/imports/" + id));
    assertThat(job.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(job.path("rowsUnchanged").asInt()).isEqualTo(2);
    assertThat(json(supplier.get("/api/v1/products")).path("totalItems").asLong()).isEqualTo(2);
  }

  @Test
  void rejectsUnusableFilesWithAClearMessage() {
    HttpResponse<String> latin1 =
        supplier.upload(
            "/api/v1/imports",
            "latin1.csv",
            "sku,name\nA,Café\n".getBytes(StandardCharsets.ISO_8859_1));
    assertThat(latin1.statusCode()).isEqualTo(422);
    assertThat(json(latin1).path("detail").asString()).contains("CSV UTF-8");

    HttpResponse<String> headerOnly =
        supplier.upload("/api/v1/imports", "h.csv", "sku\n".getBytes(StandardCharsets.UTF_8));
    assertThat(headerOnly.statusCode()).isEqualTo(422);

    byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
    assertThat(supplier.upload("/api/v1/imports", "big.csv", tooBig).statusCode()).isEqualTo(413);
  }

  @Test
  void needsSkuNameAndPriceColumnsAndStartsOnlyOnce() {
    String id = id(upload("m.csv", "sku,name,price\nA,A,1\n"));

    HttpResponse<String> noPrice = start(id, "{\"sku\": 0, \"name\": 1}", ".", false);
    assertThat(noPrice.statusCode()).isEqualTo(422);
    assertThat(json(noPrice).path("detail").asString()).contains("SKU, name and price");

    assertThat(start(id, mapping(0, 0, 2), ".", false).statusCode()).isEqualTo(422);
    assertThat(start(id, mapping(0, 1, 9), ".", false).statusCode()).isEqualTo(422);

    assertThat(start(id, mapping(0, 1, 2), ".", false).statusCode()).isEqualTo(202);
    assertThat(start(id, mapping(0, 1, 2), ".", false).statusCode()).isEqualTo(422);
  }

  private JsonNode upload(String fileName, String csv) {
    HttpResponse<String> response =
        supplier.upload("/api/v1/imports", fileName, csv.getBytes(StandardCharsets.UTF_8));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response);
  }

  private HttpResponse<String> start(String id, String mapping, String decimal, boolean complete) {
    return supplier.post(
        "/api/v1/imports/" + id + "/start",
        """
        {"mapping": %s, "decimalSeparator": "%s", "completeCatalog": %s}
        """
            .formatted(mapping, decimal, complete));
  }

  private JsonNode startAndWait(String id, String mapping, String decimal, boolean complete) {
    HttpResponse<String> started = start(id, mapping, decimal, complete);
    assertThat(started.statusCode()).as(started.body()).isEqualTo(202);
    return Eventually.until(
        () -> json(supplier.get("/api/v1/imports/" + id)),
        job ->
            job.path("status").asString().startsWith("COMPLETED")
                || job.path("status").asString().equals("FAILED"),
        Duration.ofSeconds(30));
  }

  private static String mapping(int sku, int name, int price) {
    return "{\"sku\": %d, \"name\": %d, \"price\": %d}".formatted(sku, name, price);
  }

  private static String id(JsonNode upload) {
    return upload.path("importJob").path("id").asString();
  }
}
