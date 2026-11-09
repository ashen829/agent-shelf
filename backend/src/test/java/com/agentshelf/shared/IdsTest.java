package com.agentshelf.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

  @Test
  void createsVersion7RfcVariantUuids() {
    UUID id = Ids.newId();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void embedsTheTimestampSoIdsSortByCreationTime() {
    UUID earlier = Ids.newId(1_700_000_000_000L);
    UUID later = Ids.newId(1_700_000_000_001L);

    assertThat(earlier.getMostSignificantBits() >>> 16).isEqualTo(1_700_000_000_000L);
    assertThat(earlier.toString()).isLessThan(later.toString());
  }

  @Test
  void idsAreUnique() {
    Set<UUID> ids = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      ids.add(Ids.newId());
    }
    assertThat(ids).hasSize(10_000);
  }
}
