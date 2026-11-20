package com.agentshelf.enrichment;

import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.json;
import static com.agentshelf.catalog.internal.web.ProductApiTestSupport.signedInSupplier;
import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** AI cleanup is off by default (feature flag): nothing can start it and no key is needed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EnrichmentSwitchedOffIT {

  @LocalServerPort private int port;

  @Test
  void cannotBeStartedWhileSwitchedOff() {
    BrowserClient supplier = signedInSupplier(port, "USD");

    assertThat(json(supplier.get("/api/v1/enrichment")).path("enabled").asBoolean()).isFalse();
    HttpResponse<String> start = supplier.post("/api/v1/enrichment/jobs", "{}");
    assertThat(start.statusCode()).isEqualTo(422);
    assertThat(json(start).path("detail").asString()).contains("not switched on");
  }
}
