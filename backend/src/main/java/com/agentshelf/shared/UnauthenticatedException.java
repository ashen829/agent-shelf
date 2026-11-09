package com.agentshelf.shared;

/**
 * The caller is not signed in, or the credentials are wrong. Maps to HTTP 401.
 *
 * <p>The message is shown to the client: keep it generic so it never reveals whether an account
 * exists.
 */
public class UnauthenticatedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnauthenticatedException(String message) {
    super(message);
  }
}
