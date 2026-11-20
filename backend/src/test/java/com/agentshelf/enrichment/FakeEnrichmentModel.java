package com.agentshelf.enrichment;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProposedChange;
import com.agentshelf.enrichment.internal.domain.TokenUsage;
import com.agentshelf.enrichment.internal.model.ProductEnrichmentModel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for Claude in integration tests: the test decides what each product gets back and when
 * the batch has ended. Batches are kept per ID, so tests running in parallel don't mix.
 */
public class FakeEnrichmentModel implements ProductEnrichmentModel {

  private final Map<String, List<ProductSnapshot>> submitted = new ConcurrentHashMap<>();
  private final Map<String, Optional<BatchOutcome>> outcomes = new ConcurrentHashMap<>();
  private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
  public volatile List<String> lastCategories = List.of();

  @Override
  public String modelName() {
    return "fake-model";
  }

  @Override
  public String submit(List<ProductSnapshot> products, List<String> categories) {
    String batchId = "batch-" + UUID.randomUUID();
    submitted.put(batchId, List.copyOf(products));
    lastCategories = List.copyOf(categories);
    return batchId;
  }

  @Override
  public Optional<BatchOutcome> poll(String batchId) {
    RuntimeException failure = failures.get(batchId);
    if (failure != null) {
      throw failure;
    }
    return outcomes.getOrDefault(batchId, Optional.empty());
  }

  public List<ProductSnapshot> products(String batchId) {
    return submitted.getOrDefault(batchId, List.of());
  }

  /** Ends the batch: each product's proposals come from the function. */
  public void finish(
      String batchId, Function<ProductSnapshot, List<ProposedChange>> answer, TokenUsage usage) {
    Map<UUID, List<ProposedChange>> proposals = new ConcurrentHashMap<>();
    for (ProductSnapshot product : products(batchId)) {
      proposals.put(product.id(), answer.apply(product));
    }
    outcomes.put(batchId, Optional.of(new BatchOutcome(proposals, 0, usage)));
  }

  public void failPolling(String batchId, RuntimeException failure) {
    failures.put(batchId, failure);
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {
    @Bean
    @Primary
    FakeEnrichmentModel fakeEnrichmentModel() {
      return new FakeEnrichmentModel();
    }
  }
}
