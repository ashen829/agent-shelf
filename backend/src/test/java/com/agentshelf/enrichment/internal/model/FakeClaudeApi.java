package com.agentshelf.enrichment.internal.model;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny stand-in for the Message Batches API, on a random local port. Tests never call the real
 * API. It records request bodies and serves one batch whose status and results the test sets.
 */
final class FakeClaudeApi implements AutoCloseable {

  static final String BATCH_ID = "msgbatch_test";

  private final HttpServer server;
  final List<String> createBodies = new CopyOnWriteArrayList<>();
  final List<String> requestPaths = new CopyOnWriteArrayList<>();
  final List<String> apiKeys = new CopyOnWriteArrayList<>();
  volatile String status = "in_progress";
  volatile List<String> resultLines = new ArrayList<>();

  FakeClaudeApi() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.start();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    requestPaths.add(exchange.getRequestMethod() + " " + path);
    // The SDK sends the key as "x-api-key" or as a bearer token, depending on its version.
    String apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
    String bearer = exchange.getRequestHeaders().getFirst("Authorization");
    apiKeys.add(apiKey != null ? apiKey : String.valueOf(bearer).replaceFirst("^Bearer ", ""));
    byte[] body = exchange.getRequestBody().readAllBytes();
    if (exchange.getRequestMethod().equals("POST") && path.equals("/v1/messages/batches")) {
      createBodies.add(new String(body, StandardCharsets.UTF_8));
      respond(exchange, "application/json", batch("in_progress"));
    } else if (path.equals("/v1/messages/batches/" + BATCH_ID)) {
      respond(exchange, "application/json", batch(status));
    } else if (path.equals("/v1/messages/batches/" + BATCH_ID + "/results")) {
      respond(exchange, "application/x-jsonl", String.join("\n", resultLines) + "\n");
    } else {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
    }
  }

  private String batch(String processingStatus) {
    boolean ended = processingStatus.equals("ended");
    return """
    {"id": "%s", "type": "message_batch", "processing_status": "%s",
     "request_counts": {"processing": 0, "succeeded": 0, "errored": 0, "canceled": 0,
                        "expired": 0},
     "created_at": "2026-11-20T10:00:00Z", "expires_at": "2026-11-21T10:00:00Z",
     "ended_at": %s, "archived_at": null, "cancel_initiated_at": null,
     "results_url": %s}
    """
        .formatted(
            BATCH_ID,
            processingStatus,
            ended ? "\"2026-11-20T10:05:00Z\"" : "null",
            ended ? "\"" + baseUrl() + "/v1/messages/batches/" + BATCH_ID + "/results\"" : "null");
  }

  private static void respond(HttpExchange exchange, String type, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", type);
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  /** One succeeded result whose answer is this text. */
  static String succeeded(String customId, String stopReason, String text, int in, int out) {
    String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
    return """
    {"custom_id": "%s", "result": {"type": "succeeded", "message": {"id": "msg_1", \
    "type": "message", "role": "assistant", "model": "claude-haiku-5-5", "content": \
    [{"type": "text", "text": "%s"}], "stop_reason": "%s", "stop_sequence": null, \
    "usage": {"input_tokens": %d, "output_tokens": %d, "cache_read_input_tokens": 7, \
    "cache_creation_input_tokens": 0}}}}\
    """
        .formatted(customId, escaped, stopReason, in, out);
  }

  static String errored(String customId) {
    return """
    {"custom_id": "%s", "result": {"type": "errored", "error": {"type": "error", \
    "error": {"type": "overloaded_error", "message": "Overloaded"}}}}\
    """
        .formatted(customId);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
