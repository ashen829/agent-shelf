package com.agentshelf.identity.internal.domain;

import java.util.Locale;

/** Email addresses are stored trimmed and lower-cased, so lookups ignore case. */
public final class EmailAddress {

  private EmailAddress() {}

  public static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
