package com.agentshelf.identity.internal.domain;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** URL-safe identifiers derived from a company name, for example {@code acme-hardware}. */
public final class Slugs {

  static final int MAX_BASE_LENGTH = 50;
  private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private Slugs() {}

  /** Lower-case ASCII letters and digits separated by single hyphens; "supplier" if empty. */
  public static String fromName(String name) {
    String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    String slug =
        ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
    if (slug.length() > MAX_BASE_LENGTH) {
      slug = slug.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
    }
    return slug.isEmpty() ? "supplier" : slug;
  }

  /** The base slug plus a short random suffix, used when the base is already taken. */
  public static String withRandomSuffix(String base) {
    StringBuilder slug = new StringBuilder(base).append('-');
    for (int i = 0; i < 6; i++) {
      slug.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
    }
    return slug.toString();
  }
}
