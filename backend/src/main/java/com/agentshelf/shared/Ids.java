package com.agentshelf.shared;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Time-ordered UUIDs (version 7, RFC 9562) for primary keys. They sort by creation time, which
 * keeps B-tree indexes compact, and they can be created without a database round trip.
 */
public final class Ids {

  private static final SecureRandom RANDOM = new SecureRandom();

  private Ids() {}

  public static UUID newId() {
    return newId(System.currentTimeMillis());
  }

  static UUID newId(long epochMillis) {
    byte[] random = new byte[10];
    RANDOM.nextBytes(random);
    ByteBuffer buffer = ByteBuffer.wrap(random);
    long randA = buffer.getShort() & 0x0FFFL;
    long randB = buffer.getLong();
    long msb = (epochMillis << 16) | 0x7000L | randA;
    long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(msb, lsb);
  }
}
