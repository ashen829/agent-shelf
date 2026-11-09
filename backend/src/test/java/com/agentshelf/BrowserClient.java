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
import org.jspecify.annotations.Nullable;

/**
 * A test client that behaves like the Angular app in a browser: keeps cookies between requests and
 * copies the {@code XSRF-TOKEN} cookie into the {@code X-XSRF-TOKEN} header on writes.
 */
public final class BrowserClient {

  private final String baseUrl;
  private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private final HttpClient http;
  private boolean sendCsrfHeader = true;

  private BrowserClient(int port) {
    this.baseUrl = "http://localhost:" + port;
    this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
  }

  /** Opens the app: one "who am I" call, as the Angular app does at start-up. */
  public static BrowserClient open(int port) {
    BrowserClient browser = new BrowserClient(port);
    browser.get("/api/v1/auth/me");
    return browser;
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
    return write("POST", path, json);
  }

  /** A multipart/form-data upload with one file part named "file", like a browser form. */
  public HttpResponse<String> upload(String path, String fileName, byte[] content) {
    String boundary = "----agentshelf-test-" + java.util.UUID.randomUUID();
    byte[] head =
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + fileName
                + "\"\r\nContent-Type: text/csv\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] tail =
        ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] body = new byte[head.length + content.length + tail.length];
    System.arraycopy(head, 0, body, 0, head.length);
    System.arraycopy(content, 0, body, head.length, content.length);
    System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    if (sendCsrfHeader) {
      cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
    }
    return send(request);
  }

  /** The raw bytes of a download. */
  public HttpResponse<byte[]> download(String path) {
    try {
      return http.send(
          HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public HttpResponse<String> put(String path, String json) {
    return write("PUT", path, json);
  }

  public HttpResponse<String> delete(String path) {
    return write("DELETE", path, null);
  }

  private HttpResponse<String> write(String method, String path, @Nullable String json) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .method(
                method,
                json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
    if (json != null) {
      request.header("Content-Type", "application/json");
    }
    if (sendCsrfHeader) {
      cookie("XSRF-TOKEN").ifPresent(token -> request.header("X-XSRF-TOKEN", token));
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
