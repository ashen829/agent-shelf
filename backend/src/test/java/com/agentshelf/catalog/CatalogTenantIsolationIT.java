package com.agentshelf.catalog;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.product;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** One supplier can never read, change or even detect another supplier's products. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CatalogTenantIsolationIT {

  @LocalServerPort private int port;
  private BrowserClient supplierA;
  private BrowserClient supplierB;
  private String productOfA;

  @BeforeEach
  void twoSuppliers() {
    supplierA = signedInSupplier(port, "USD");
    supplierB = signedInSupplier(port, "EUR");
    productOfA =
        json(supplierA.post("/api/v1/products", product("SHARED-SKU", "10"))).path("id").asString();
  }

  @Test
  void anotherSuppliersProductLooksLikeItDoesNotExist() {
    String path = "/api/v1/products/" + productOfA;

    assertThat(supplierB.get(path).statusCode()).isEqualTo(404);
    assertThat(supplierB.put(path, product("SHARED-SKU", "1", 0)).statusCode()).isEqualTo(404);
    assertThat(supplierB.delete(path).statusCode()).isEqualTo(404);
    assertThat(supplierB.post(path + "/restore", "{}").statusCode()).isEqualTo(404);

    assertThat(json(supplierA.get(path)).path("status").asString()).isEqualTo("ACTIVE");
    assertThat(json(supplierA.get(path)).path("price").path("amount").asString())
        .isEqualTo("10.0000");
  }

  @Test
  void listsOnlyShowTheCallersProducts() {
    assertThat(json(supplierB.get("/api/v1/products")).path("totalItems").asLong()).isZero();
    assertThat(json(supplierB.get("/api/v1/products?status=ARCHIVED")).path("totalItems").asLong())
        .isZero();
  }

  @Test
  void skusOnlyNeedToBeUniqueWithinOneSupplier() {
    assertThat(supplierB.post("/api/v1/products", product("SHARED-SKU", "5")).statusCode())
        .isEqualTo(201);
  }
}
