package com.agentshelf.catalog.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import java.net.http.HttpResponse;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Helpers for product API tests: a signed-in supplier and product request bodies. */
public final class ProductApiTestSupport {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ProductApiTestSupport() {}

  public static BrowserClient signedInSupplier(int port, String currency) {
    BrowserClient browser = BrowserClient.open(port);
    HttpResponse<String> signup =
        browser.post(
            "/api/v1/auth/signup",
            """
            {"companyName": "Catalog Test", "currency": "%s",
             "email": "catalog-%s@example.com", "password": "a long enough password"}
            """
                .formatted(currency, UUID.randomUUID()));
    assertThat(signup.statusCode()).isEqualTo(201);
    return browser;
  }

  public static String product(String sku, String price) {
    return product(sku, price, "PVC pipe 20 mm");
  }

  public static String product(String sku, String price, String name) {
    return """
    {"sku": "%s", "name": "%s", "category": "Pipes", "unit": "m", "price": "%s", "stockQty": 100}
    """
        .formatted(sku, name, price);
  }

  public static String product(String sku, String price, long version) {
    return """
    {"sku": "%s", "name": "PVC pipe 20 mm", "price": "%s", "stockQty": 100, "version": %d}
    """
        .formatted(sku, price, version);
  }

  public static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }
}
