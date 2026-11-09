package com.agentshelf;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * A test client that behaves like the Angular app in a browser: keeps cookies between requests and
 * copies the {@code XSRF-TOKEN} cookie into the {@code X-XSRF-TOKEN} header on writes.
 */
public final class BrowserClient {

  private final String baseUrl;
  private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private final HttpClient http;
  private boolean sendCsrfHeader = true;

  public BrowserClient(int port) {
    this.baseUrl = "http://localhost:" + port;
    this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  /** Simulates a cross-site request: the attacker's page can't read our CSRF cookie. */
  public BrowserClient withoutCsrfHeader() {
    sendCsrfHeader = false;
    return this;
  }

  public HttpResponse<String> get(String path) {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
  }

  public HttpResponse<String> post(String path, String json) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
    if (sendCsrfHeader) {
      csrfToken().ifPresent(token -> request.header("X-XSRF-TOKEN", token));
    }
    return send(request);
  }

  public Optional<String> cookie(String name) {
    return cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals(name) && !c.hasExpired())
        .map(HttpCookie::getValue)
        .filter(value -> !value.isEmpty())
        .findFirst();
  }

  private Optional<String> csrfToken() {
    if (cookie("XSRF-TOKEN").isEmpty()) {
      get("/api/v1/auth/me");
    }
    return cookie("XSRF-TOKEN");
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return http.send(
          request.header("Accept", "application/json").build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
