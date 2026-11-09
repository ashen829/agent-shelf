package com.agentshelf.shared;

/**
 * The request conflicts with the current state, for example a duplicate SKU. Maps to HTTP 409.
 *
 * <p>The message is shown to the client, so never put secrets, stack details or raw untrusted input
 * in it.
 */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message);
  }
}
