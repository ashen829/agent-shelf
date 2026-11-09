package com.agentshelf.shared;

/**
 * Media type of error responses (RFC 9457 problem details). Use it in {@code @ApiResponse}
 * annotations so the OpenAPI spec documents errors the same way in every module.
 */
public final class ProblemResponses {

  public static final String MEDIA_TYPE = "application/problem+json";

  private ProblemResponses() {}
}
