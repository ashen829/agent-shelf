package com.agentshelf.shared;

/**
 * A business rule rejected the request. Maps to HTTP 422.
 *
 * <p>The message is shown to the client, so never put secrets, stack details or raw untrusted input
 * in it.
 */
public class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DomainException(String message) {
    super(message);
  }
}
