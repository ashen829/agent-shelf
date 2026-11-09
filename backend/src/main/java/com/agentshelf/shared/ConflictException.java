package com.agentshelf.shared;

import org.jspecify.annotations.Nullable;

/**
 * The request conflicts with the current state, for example a duplicate SKU. Maps to HTTP 409.
 *
 * <p>The message is shown to the client, so never put secrets, stack details or raw untrusted input
 * in it. When the conflict is about one input field, name it so the form can show the message under
 * that field.
 */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final @Nullable String field;

  public ConflictException(String message) {
    this(message, null);
  }

  public ConflictException(String message, @Nullable String field) {
    super(message);
    this.field = field;
  }

  public @Nullable String field() {
    return field;
  }
}
