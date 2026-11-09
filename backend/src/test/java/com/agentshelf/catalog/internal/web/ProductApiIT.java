package com.agentshelf.catalog.internal.web;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.product;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** The product API over real HTTP: session, CSRF, validation, paging and optimistic locking. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductApiIT {

  @LocalServerPort private int port;
  private BrowserClient supplier;

  @BeforeEach
  void signUp() {
    supplier = signedInSupplier(port, "LKR");
  }

  @Test
  void createsReadsAndListsAProductInTheSuppliersCurrency() {
    HttpResponse<String> created = supplier.post("/api/v1/products", product("PVC-20", "0.0125"));

    assertThat(created.statusCode()).isEqualTo(201);
    JsonNode body = json(created);
    assertThat(body.path("sku").asString()).isEqualTo("PVC-20");
    assertThat(body.path("price").path("amount").asString()).isEqualTo("0.0125");
    assertThat(body.path("price").path("currency").asString()).isEqualTo("LKR");
    assertThat(body.path("status").asString()).isEqualTo("ACTIVE");
    assertThat(body.path("description").isNull()).isTrue();

    String id = body.path("id").asString();
    assertThat(json(supplier.get("/api/v1/products/" + id)).path("name").asString())
        .isEqualTo("PVC pipe 20 mm");
    JsonNode page = json(supplier.get("/api/v1/products"));
    assertThat(page.path("totalItems").asLong()).isEqualTo(1);
    assertThat(page.path("items").get(0).path("id").asString()).isEqualTo(id);
  }

  @Test
  void duplicateSkuIsAConflictOnTheSkuFieldIgnoringCase() {
    supplier.post("/api/v1/products", product("PVC-20", "1"));

    HttpResponse<String> duplicate = supplier.post("/api/v1/products", product("pvc-20", "2"));

    assertThat(duplicate.statusCode()).isEqualTo(409);
    assertThat(json(duplicate).path("errors").get(0).path("field").asString()).isEqualTo("sku");
  }

  @Test
  void invalidFieldsAreListed() {
    HttpResponse<String> response =
        supplier.post(
            "/api/v1/products",
            """
            {"sku": "has space", "name": "", "price": "1.23456", "stockQty": -1}
            """);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json(response).path("errors").valueStream().map(e -> e.path("field").asString()))
        .contains("sku", "name", "price", "stockQty");
  }

  @Test
  void editingSavesChangesAndRejectsAStaleVersion() {
    JsonNode created = json(supplier.post("/api/v1/products", product("PVC-20", "4.50")));
    String id = created.path("id").asString();
    long version = created.path("version").asLong();

    HttpResponse<String> saved =
        supplier.put("/api/v1/products/" + id, product("PVC-20", "4.75", version));
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(json(saved).path("price").path("amount").asString()).isEqualTo("4.7500");
    assertThat(json(saved).path("version").asLong()).isGreaterThan(version);

    // Another tab still has the old version.
    HttpResponse<String> stale =
        supplier.put("/api/v1/products/" + id, product("PVC-20", "9.99", version));
    assertThat(stale.statusCode()).isEqualTo(409);
    assertThat(json(supplier.get("/api/v1/products/" + id)).path("price").path("amount").asString())
        .isEqualTo("4.7500");
  }

  @Test
  void editingWithoutAVersionIsRejected() {
    String id =
        json(supplier.post("/api/v1/products", product("PVC-20", "1"))).path("id").asString();

    assertThat(supplier.put("/api/v1/products/" + id, product("PVC-20", "2")).statusCode())
        .isEqualTo(422);
  }

  @Test
  void archivedProductsLeaveTheListAndCanBeRestored() {
    String id =
        json(supplier.post("/api/v1/products", product("PVC-20", "1"))).path("id").asString();

    assertThat(supplier.delete("/api/v1/products/" + id).statusCode()).isEqualTo(204);
    assertThat(json(supplier.get("/api/v1/products")).path("totalItems").asLong()).isZero();
    assertThat(json(supplier.get("/api/v1/products?status=ARCHIVED")).path("totalItems").asLong())
        .isEqualTo(1);

    HttpResponse<String> restored = supplier.post("/api/v1/products/" + id + "/restore", "{}");
    assertThat(json(restored).path("status").asString()).isEqualTo("ACTIVE");
    assertThat(json(supplier.get("/api/v1/products")).path("totalItems").asLong()).isEqualTo(1);
  }

  @Test
  void pagesAreSortedAndBounded() {
    for (String name : new String[] {"Cement", "Anchor bolt", "Brick"}) {
      supplier.post("/api/v1/products", product(name.replace(' ', '-'), "1", name));
    }

    JsonNode first = json(supplier.get("/api/v1/products?size=2&sort=NAME"));
    assertThat(first.path("items").valueStream().map(i -> i.path("name").asString()))
        .containsExactly("Anchor bolt", "Brick");
    assertThat(first.path("totalPages").asInt()).isEqualTo(2);
    assertThat(supplier.get("/api/v1/products?size=101").statusCode()).isEqualTo(400);
    assertThat(supplier.get("/api/v1/products?sort=price;drop").statusCode()).isEqualTo(400);
    assertThat(supplier.get("/api/v1/products?page=-1").statusCode()).isEqualTo(400);
  }

  @Test
  void namesSortTheWayPeopleReadThemIgnoringCase() {
    for (String name : new String[] {"PVC pipe", "anchor bolt", "Portland cement", "Brick"}) {
      supplier.post("/api/v1/products", product(name.replace(' ', '-'), "1", name));
    }

    JsonNode page = json(supplier.get("/api/v1/products?sort=NAME"));

    assertThat(page.path("items").valueStream().map(i -> i.path("name").asString()))
        .containsExactly("anchor bolt", "Brick", "Portland cement", "PVC pipe");
  }

  @Test
  void anonymousCallersGet401() {
    BrowserClient anonymous = BrowserClient.open(port);

    assertThat(anonymous.get("/api/v1/products").statusCode()).isEqualTo(401);
    assertThat(anonymous.post("/api/v1/products", product("X", "1")).statusCode()).isEqualTo(401);
  }
}
