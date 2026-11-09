package com.agentshelf.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import com.agentshelf.identity.internal.application.AccountQueries;
import com.agentshelf.identity.internal.persistence.AppUserRepository;
import com.agentshelf.shared.UnauthenticatedException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One supplier can never read another supplier's identity data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TenantIsolationIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort private int port;
  @Autowired private AccountQueries accounts;
  @Autowired private AppUserRepository users;

  private BrowserClient browserA;
  private JsonNode accountA;
  private JsonNode accountB;

  @BeforeEach
  void twoSuppliers() {
    browserA = new BrowserClient(port);
    accountA = signup(browserA, "Supplier A");
    accountB = signup(new BrowserClient(port), "Supplier B");
  }

  @Test
  void meReturnsOnlyTheCallersOwnSupplier() {
    JsonNode me = JSON.readTree(browserA.get("/api/v1/auth/me").body());

    assertThat(me.path("supplier").path("id").asString())
        .isEqualTo(accountA.path("supplier").path("id").asString())
        .isNotEqualTo(accountB.path("supplier").path("id").asString());
    assertThat(me.toString()).doesNotContain(accountB.path("email").asString());
  }

  @Test
  void accountQueryRejectsAUserPairedWithAnotherSupplier() {
    CurrentUser forged = new CurrentUser(userId(accountA), supplierId(accountB), Role.OWNER);

    assertThatThrownBy(() -> accounts.account(forged)).isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  void repositoryLookupIsScopedBySupplier() {
    assertThat(users.findByIdAndSupplierId(userId(accountA), supplierId(accountB))).isEmpty();
    assertThat(users.findByIdAndSupplierId(userId(accountA), supplierId(accountA))).isPresent();
  }

  private static UUID userId(JsonNode account) {
    return UUID.fromString(account.path("userId").asString());
  }

  private static UUID supplierId(JsonNode account) {
    return UUID.fromString(account.path("supplier").path("id").asString());
  }

  private static JsonNode signup(BrowserClient browser, String company) {
    String body =
        """
        {"companyName": "%s", "currency": "USD", "email": "%s", "password": "a long enough password"}
        """
            .formatted(company, "tenant-" + UUID.randomUUID() + "@example.com");
    return JSON.readTree(browser.post("/api/v1/auth/signup", body).body());
  }
}
