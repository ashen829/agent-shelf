package com.agentshelf.catalog.internal.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.catalog.internal.domain.ColumnMapping;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeaderMatcherTest {

  @Test
  void matchesCommonHeaderNamesIgnoringCaseAndPunctuation() {
    ColumnMapping m =
        HeaderMatcher.suggest(
            List.of("Item Code", "Product Name", "Unit Price (LKR)", "UOM", "Qty", "Notes"));

    assertThat(m.sku()).isEqualTo(0);
    assertThat(m.name()).isEqualTo(1);
    assertThat(m.price()).isNull();
    assertThat(m.unit()).isEqualTo(3);
    assertThat(m.stockQty()).isEqualTo(4);
    assertThat(m.description()).isNull();
  }

  @Test
  void usesEachColumnOnlyOnce() {
    ColumnMapping m = HeaderMatcher.suggest(List.of("SKU", "Item", "Price"));

    assertThat(m.sku()).isEqualTo(0);
    assertThat(m.name()).isEqualTo(1);
    assertThat(m.price()).isEqualTo(2);
  }
}
