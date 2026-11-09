package com.agentshelf.shared;

/**
 * A requested resource does not exist, or belongs to another supplier. Maps to HTTP 404.
 *
 * <p>The message is shown to the client, so never put secrets, stack details or raw untrusted input
 * in it.
 */
public class NotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }
}
