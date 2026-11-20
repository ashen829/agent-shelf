package com.agentshelf.enrichment.internal.domain;

/**
 * One change exactly as the AI model proposed it. Untrusted: it only becomes a suggestion after
 * {@link SuggestionRules#check} accepts it.
 */
public record ProposedChange(String field, String value, String reason, double confidence) {}
