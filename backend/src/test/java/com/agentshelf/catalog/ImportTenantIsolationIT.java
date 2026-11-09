package com.agentshelf.catalog;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.Eventually;
import com.agentshelf.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/**
 * One supplier's imports, errors and files are invisible to others, and touch only their own
 * products.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ImportTenantIsolationIT {

  @LocalServerPort private int port;

  @Test
  void importsStayWithinOneSupplier() {
    BrowserClient a = signedInSupplier(port, "USD");
    BrowserClient b = signedInSupplier(port, "EUR");
    b.post(
        "/api/v1/products",
        "{\"sku\": \"SHARED\", \"name\": \"B's own product\", \"price\": \"9\", \"stockQty\": 1}");

    String id =
        json(a.upload(
                "/api/v1/imports",
                "a.csv",
                "sku,name,price\nSHARED,A's product,1\nBAD,,x\n".getBytes(StandardCharsets.UTF_8)))
            .path("importJob")
            .path("id")
            .asString();
    a.post(
        "/api/v1/imports/" + id + "/start",
        "{\"mapping\": {\"sku\": 0, \"name\": 1, \"price\": 2}, \"decimalSeparator\": \".\", \"completeCatalog\": true}");
    Eventually.until(
        () -> json(a.get("/api/v1/imports/" + id)),
        job -> job.path("status").asString().startsWith("COMPLETED"),
        Duration.ofSeconds(30));

    String path = "/api/v1/imports/" + id;
    assertThat(b.get(path).statusCode()).isEqualTo(404);
    assertThat(b.get(path + "/preview").statusCode()).isEqualTo(404);
    assertThat(b.get(path + "/errors").statusCode()).isEqualTo(404);
    assertThat(b.download(path + "/errors.csv").statusCode()).isEqualTo(404);
    assertThat(
            b.post(
                    path + "/start",
                    "{\"mapping\": {\"sku\": 0, \"name\": 1, \"price\": 2}, \"decimalSeparator\": \".\", \"completeCatalog\": false}")
                .statusCode())
        .isEqualTo(404);
    assertThat(json(b.get("/api/v1/imports")).path("totalItems").asLong()).isZero();

    // A's "complete catalog" import did not touch B's product with the same SKU.
    JsonNode bProducts = json(b.get("/api/v1/products"));
    assertThat(bProducts.path("totalItems").asLong()).isEqualTo(1);
    assertThat(bProducts.path("items").get(0).path("name").asString()).isEqualTo("B's own product");
    assertThat(json(a.get("/api/v1/products")).path("items").get(0).path("name").asString())
        .isEqualTo("A's product");
  }
}
