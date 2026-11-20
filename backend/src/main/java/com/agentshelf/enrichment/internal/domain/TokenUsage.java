package com.agentshelf.enrichment.internal.domain;

/** Tokens used by one batch, summed over its requests. Stored for every job (cost tracking). */
public record TokenUsage(
    long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

  public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

  public TokenUsage plus(TokenUsage other) {
    return new TokenUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens + other.cacheWriteTokens);
  }
}
