package com.agentshelf.enrichment.internal.model;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude adapter for {@link ProductEnrichmentModel}: one Message Batches request per product (50%
 * cheaper than one-by-one calls), a shared system prompt marked for caching, low effort and a
 * strict JSON output schema. Results are parsed here; {@code SuggestionRules} checks them
 * afterwards.
 */
public class ClaudeBatchEnrichmentModel implements ProductEnrichmentModel, AutoCloseable {

  /** Room for adaptive thinking at low effort plus a few short changes. */
  static final long MAX_TOKENS = 4096;

  private static final Logger log = LoggerFactory.getLogger(ClaudeBatchEnrichmentModel.class);

  private final AnthropicClient client;
  private final String model;
  private final JsonMapper json;

  public ClaudeBatchEnrichmentModel(AnthropicClient client, String model, JsonMapper json) {
    this.client = client;
    this.model = model;
    this.json = json;
  }

  /** Spring calls this on shutdown: releases the HTTP client's threads and connections. */
  @Override
  public void close() {
    client.close();
  }

  @Override
  public String modelName() {
    return model;
  }

  @Override
  public String submit(List<ProductSnapshot> products, List<String> categories) {
    if (products.isEmpty()) {
      throw new IllegalArgumentException("A batch needs at least one product.");
    }
    BatchCreateParams.Builder batch = BatchCreateParams.builder();
    String categoryList = escapeTags(json.writeValueAsString(categories));
    for (ProductSnapshot product : products) {
      batch.addRequest(
          BatchCreateParams.Request.builder()
              .customId(product.id().toString())
              .params(params(product, categoryList))
              .build());
    }
    MessageBatch created = client.messages().batches().create(batch.build());
    log.info("Submitted enrichment batch {} with {} products", created.id(), products.size());
    return created.id();
  }

  private BatchCreateParams.Request.Params params(ProductSnapshot product, String categoryList) {
    return BatchCreateParams.Request.Params.builder()
        .model(model)
        .maxTokens(MAX_TOKENS)
        .systemOfTextBlockParams(
            List.of(
                TextBlockParam.builder()
                    .text(CleanupPrompt.SYSTEM)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()))
        .outputConfig(
            OutputConfig.builder()
                .effort(OutputConfig.Effort.LOW)
                .format(
                    JsonOutputFormat.builder()
                        .schema(
                            JsonOutputFormat.Schema.builder()
                                .putAllAdditionalProperties(schemaProperties())
                                .build())
                        .build())
                .build())
        .addUserMessage(userMessage(product, categoryList))
        .build();
  }

  /**
   * The product as JSON inside tags, so its text can't break out of the data section. JSON encoding
   * escapes quotes and line breaks in the product's own text; {@code <} and {@code >} are escaped
   * too, so a name like {@code </product>} can't close the tag.
   */
  String userMessage(ProductSnapshot product, String categoryList) {
    Map<String, @Nullable Object> fields = new LinkedHashMap<>();
    fields.put("sku", product.sku());
    fields.put("name", product.name());
    fields.put("category", product.category());
    fields.put("unit", product.unit());
    fields.put("description", product.description());
    return "The supplier's categories: "
        + categoryList
        + "\n\n<product>\n"
        + escapeTags(json.writeValueAsString(fields))
        + "\n</product>";
  }

  /** In JSON, {@code <} and {@code >} only occur inside strings, where these escapes are valid. */
  private static String escapeTags(String jsonText) {
    return jsonText.replace("<", "\\u003c").replace(">", "\\u003e");
  }

  private static Map<String, JsonValue> schemaProperties() {
    return CleanupPrompt.OUTPUT_SCHEMA.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonValue.from(e.getValue())));
  }

  @Override
  public Optional<BatchOutcome> poll(String batchId) {
    MessageBatch batch = client.messages().batches().retrieve(batchId);
    if (!batch.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)) {
      return Optional.empty();
    }
    Map<UUID, List<ProposedChange>> proposals = new HashMap<>();
    int failed = 0;
    TokenUsage usage = TokenUsage.NONE;
    try (StreamResponse<MessageBatchIndividualResponse> results =
        client.messages().batches().resultsStreaming(batchId)) {
      for (MessageBatchIndividualResponse result :
          (Iterable<MessageBatchIndividualResponse>) results.stream()::iterator) {
        Optional<UUID> productId = productId(result.customId());
        if (productId.isEmpty() || !result.result().isSucceeded()) {
          failed++;
          continue;
        }
        Message message = result.result().asSucceeded().message();
        usage = usage.plus(usage(message.usage()));
        Optional<List<ProposedChange>> changes = changes(message);
        if (changes.isPresent()) {
          proposals.put(productId.get(), changes.get());
        } else {
          failed++;
        }
      }
    }
    return Optional.of(new BatchOutcome(proposals, failed, usage));
  }

  /** The changes in a finished answer; empty if it was cut off, refused or not valid JSON. */
  Optional<List<ProposedChange>> changes(Message message) {
    if (!message.stopReason().map(StopReason.END_TURN::equals).orElse(false)) {
      return Optional.empty();
    }
    String text =
        message.content().stream()
            .filter(ContentBlock::isText)
            .map(block -> block.asText().text())
            .collect(Collectors.joining());
    try {
      JsonNode changes = json.readTree(text).path("changes");
      if (!changes.isArray()) {
        return Optional.empty();
      }
      List<ProposedChange> result = new ArrayList<>();
      for (JsonNode change : changes) {
        if (!change.path("confidence").isNumber()) {
          return Optional.empty();
        }
        result.add(
            new ProposedChange(
                change.path("field").asString(""),
                change.path("value").asString(""),
                change.path("reason").asString(""),
                change.path("confidence").asDouble()));
      }
      return Optional.of(List.copyOf(result));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  private static Optional<UUID> productId(String customId) {
    try {
      return Optional.of(UUID.fromString(customId));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static TokenUsage usage(Usage usage) {
    return new TokenUsage(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cacheReadInputTokens().orElse(0L),
        usage.cacheCreationInputTokens().orElse(0L));
  }
}
