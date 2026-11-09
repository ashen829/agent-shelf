package com.agentshelf.identity.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.BrowserClient;
import com.agentshelf.TestcontainersConfiguration;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sign-up, login, logout and CSRF over real HTTP, with sessions stored in PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct horse battery";

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void signupCreatesSupplierAndOwnerAndSignsIn() {
    BrowserClient browser = new BrowserClient(port);
    String email = uniqueEmail();

    HttpResponse<String> signup =
        browser.post("/api/v1/auth/signup", signupJson(email, "Acme Hardware"));

    assertThat(signup.statusCode()).isEqualTo(201);
    JsonNode account = json(signup);
    assertThat(account.path("email").asString()).isEqualTo(email);
    assertThat(account.path("role").asString()).isEqualTo("OWNER");
    assertThat(account.path("supplier").path("name").asString()).isEqualTo("Acme Hardware");
    assertThat(account.path("supplier").path("slug").asString()).startsWith("acme-hardware");
    assertThat(account.path("supplier").path("currency").asString()).isEqualTo("EUR");

    HttpResponse<String> me = browser.get("/api/v1/auth/me");
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(json(me).path("userId").asString()).isEqualTo(account.path("userId").asString());

    String userId = account.path("userId").asString();
    assertThat(sessionsOf(userId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM identity.app_user WHERE email = ?", String.class, email))
        .startsWith("{bcrypt}")
        .doesNotContain(PASSWORD);
  }

  @Test
  void sessionCookieIsHttpOnlyAndCsrfCookieIsReadable() {
    BrowserClient browser = new BrowserClient(port);
    HttpResponse<String> signup =
        browser.post("/api/v1/auth/signup", signupJson(uniqueEmail(), "Cookie Test"));

    assertThat(signup.headers().allValues("Set-Cookie"))
        .anySatisfy(c ->
            assertThat(c)
                .startsWith("AGENTSHELF_SESSION=")
                .contains("HttpOnly")
                .contains("SameSite=Lax"))
        .anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"));
  }

  @Test
  void anonymousRequestsGet401AndCreateNoSession() {
    BrowserClient browser = new BrowserClient(port);
    Integer before =
        jdbc.queryForObject("SELECT count(*) FROM identity.spring_session", Integer.class);

    HttpResponse<String> me = browser.get("/api/v1/auth/me");

    assertThat(me.statusCode()).isEqualTo(401);
    assertThat(me.headers().firstValue("Content-Type"))
        .hasValueSatisfying(t -> assertThat(t).startsWith("application/problem+json"));
    assertThat(json(me).path("requestId").asString()).isNotBlank();
    assertThat(browser.cookie("XSRF-TOKEN")).isPresent();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.spring_session", Integer.class))
        .isEqualTo(before);
  }

  @Test
  void writesWithoutCsrfTokenAreRejected() {
    BrowserClient attacker = new BrowserClient(port).withoutCsrfHeader();

    HttpResponse<String> signup =
        attacker.post("/api/v1/auth/signup", signupJson(uniqueEmail(), "Forged Ltd"));

    assertThat(signup.statusCode()).isEqualTo(403);
    assertThat(json(signup).path("detail").asString()).contains("CSRF");
  }

  @Test
  void duplicateEmailIsConflictIgnoringCase() {
    String email = uniqueEmail();
    new BrowserClient(port).post("/api/v1/auth/signup", signupJson(email, "First Ltd"));

    HttpResponse<String> second =
        new BrowserClient(port)
            .post(
                "/api/v1/auth/signup",
                signupJson(email.toUpperCase(java.util.Locale.ROOT), "Second Ltd"));

    assertThat(second.statusCode()).isEqualTo(409);
    assertThat(json(second).path("detail").asString())
        .isEqualTo("An account with this email already exists.");
  }

  @Test
  void invalidSignupListsFieldErrors() {
    HttpResponse<String> signup =
        new BrowserClient(port)
            .post(
                "/api/v1/auth/signup",
                """
                {"companyName": "A", "currency": "EURO", "email": "not-an-email", "password": "short"}
                """);

    assertThat(signup.statusCode()).isEqualTo(400);
    assertThat(json(signup).path("errors").valueStream().map(e -> e.path("field").asString()))
        .contains("companyName", "currency", "email", "password");
  }

  @Test
  void wrongPasswordAndUnknownEmailGiveTheSameGenericError() {
    String email = uniqueEmail();
    new BrowserClient(port).post("/api/v1/auth/signup", signupJson(email, "Login Test"));

    HttpResponse<String> wrongPassword =
        new BrowserClient(port).post("/api/v1/auth/login", loginJson(email, "wrong password!!"));
    HttpResponse<String> unknownEmail =
        new BrowserClient(port).post("/api/v1/auth/login", loginJson(uniqueEmail(), PASSWORD));

    assertThat(wrongPassword.statusCode()).isEqualTo(401);
    assertThat(unknownEmail.statusCode()).isEqualTo(401);
    assertThat(json(wrongPassword).path("detail").asString())
        .isEqualTo("Email or password is incorrect.")
        .isEqualTo(json(unknownEmail).path("detail").asString());
  }

  @Test
  void loginThenLogoutEndsTheSession() {
    String email = uniqueEmail();
    String userId =
        json(new BrowserClient(port).post("/api/v1/auth/signup", signupJson(email, "Logout Test")))
            .path("userId")
            .asString();
    BrowserClient browser = new BrowserClient(port);

    HttpResponse<String> login =
        browser.post(
            "/api/v1/auth/login", loginJson(email.toUpperCase(java.util.Locale.ROOT), PASSWORD));
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
    assertThat(sessionsOf(userId)).isEqualTo(2);

    assertThat(browser.post("/api/v1/auth/logout", "{}").statusCode()).isEqualTo(204);

    assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
    assertThat(sessionsOf(userId)).isEqualTo(1);
  }

  @Test
  void responsesCarrySecurityHeaders() {
    HttpResponse<String> me = new BrowserClient(port).get("/api/v1/auth/me");

    assertThat(me.headers().firstValue("Content-Security-Policy"))
        .hasValue("default-src 'none'; frame-ancestors 'none'");
    assertThat(me.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(me.headers().firstValue("X-Frame-Options")).hasValue("DENY");
  }

  private int sessionsOf(String userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM identity.spring_session WHERE principal_name = ?",
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  static String uniqueEmail() {
    return "owner-" + UUID.randomUUID() + "@example.com";
  }

  static String signupJson(String email, String company) {
    return """
    {"companyName": "%s", "currency": "EUR", "email": "%s", "password": "%s"}
    """
        .formatted(company, email, PASSWORD);
  }

  static String loginJson(String email, String password) {
    return """
    {"email": "%s", "password": "%s"}
    """
        .formatted(email, password);
  }

  static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }
}
