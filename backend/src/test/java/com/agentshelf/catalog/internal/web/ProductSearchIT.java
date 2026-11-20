package com.agentshelf.catalog.internal.web;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** Searching and filtering the product list over real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductSearchIT {

  @LocalServerPort private int port;
  private BrowserClient supplier;

  @BeforeEach
  void catalog() {
    supplier = signedInSupplier(port, "LKR");
    add("PVC-20", "PVC pipe 20 mm", "Pipes", "185.50", 420);
    add("PVC-25", "PVC pipe 25 mm", "Pipes", "240", 0);
    add("SCREW-4X40", "Wood screw 4 x 40 mm", "Fasteners", "2.75", 12000);
    add("BOLT-M8", "Hex bolt M8", "Fasteners", "45", 900);
    add("TAPE-50", "Tape 50% off_cut", null, "95", 640);
    add("CEM-50", "Portland cement 50 kg", "Building materials", "2650", 0);
  }

  @Test
  void everyWordMustMatchSomewhereIgnoringCase() {
    assertThat(skus("pvc 20")).containsExactly("PVC-20");
    assertThat(skus("PIPE")).containsExactly("PVC-20", "PVC-25");
    assertThat(skus("fasteners bol")).containsExactly("BOLT-M8");
    assertThat(skus("screw-4x")).containsExactly("SCREW-4X40");
    assertThat(skus("nothing-like-this")).isEmpty();
  }

  @Test
  void wildcardCharactersAreSearchedAsPlainText() {
    assertThat(skus("50%")).containsExactly("TAPE-50");
    assertThat(skus("off_cut")).containsExactly("TAPE-50");
    assertThat(skus("%")).containsExactly("TAPE-50");
    assertThat(skus("_")).containsExactly("TAPE-50");
  }

  @Test
  void filtersByCategoryAndStock() {
    assertThat(skus("", "&category=Fasteners")).containsExactly("BOLT-M8", "SCREW-4X40");
    assertThat(skus("", "&stock=OUT_OF_STOCK")).containsExactly("CEM-50", "PVC-25");
    assertThat(skus("pvc", "&stock=IN_STOCK")).containsExactly("PVC-20");
    assertThat(skus("", "&category=Pipes&stock=IN_STOCK")).containsExactly("PVC-20");
  }

  @Test
  void sortsByPrice() {
    assertThat(skus("", "&sort=PRICE"))
        .containsExactly("SCREW-4X40", "BOLT-M8", "TAPE-50", "PVC-20", "PVC-25", "CEM-50");
    assertThat(skus("", "&sort=PRICE_DESC").getFirst()).isEqualTo("CEM-50");
  }

  @Test
  void listsCategoriesWithCountsForActiveProductsOnly() {
    String id =
        json(supplier.get("/api/v1/products?q=bolt")).path("items").get(0).path("id").asString();
    supplier.delete("/api/v1/products/" + id);

    JsonNode categories = json(supplier.get("/api/v1/products/categories"));

    assertThat(
            categories
                .path("items")
                .valueStream()
                .map(c -> c.path("name").asString() + "=" + c.path("count").asLong()))
        .containsExactly("Building materials=1", "Fasteners=1", "Pipes=2");
  }

  @Test
  void rejectsSearchTextThatIsTooLongOrHasTooManyWords() {
    assertThat(supplier.get("/api/v1/products?q=" + "x".repeat(101)).statusCode()).isEqualTo(400);
    assertThat(supplier.get("/api/v1/products?q=" + encode("a b c d e f g h i")).statusCode())
        .isEqualTo(422);
    assertThat(supplier.get("/api/v1/products?stock=SOMETIMES").statusCode()).isEqualTo(400);
  }

  private List<String> skus(String q) {
    return skus(q, "");
  }

  private List<String> skus(String q, String filters) {
    JsonNode page = json(supplier.get("/api/v1/products?size=100&q=" + encode(q) + filters));
    return page.path("items").valueStream().map(p -> p.path("sku").asString()).toList();
  }

  private void add(String sku, String name, @Nullable String category, String price, int stock) {
    supplier.post(
        "/api/v1/products",
        """
        {"sku": "%s", "name": "%s", "category": %s, "price": "%s", "stockQty": %d}
        """
            .formatted(
                sku, name, category == null ? "null" : "\"" + category + "\"", price, stock));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
