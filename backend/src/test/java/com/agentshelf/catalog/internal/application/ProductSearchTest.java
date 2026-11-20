package com.agentshelf.catalog.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentshelf.shared.DomainException;
import org.junit.jupiter.api.Test;

class ProductSearchTest {

  @Test
  void splitsIntoLowerCaseWordsAndDropsRepeats() {
    assertThat(ProductSearch.words("  PVC   pipe pvc ")).containsExactly("%pvc%", "%pipe%");
  }

  @Test
  void escapesLikeWildcardsSoTheyMatchThemselves() {
    assertThat(ProductSearch.words("50% a_b c\\d"))
        .containsExactly("%50\\%%", "%a\\_b%", "%c\\\\d%");
  }

  @Test
  void limitsLengthAndWordCount() {
    assertThatThrownBy(() -> ProductSearch.words("x".repeat(101)))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> ProductSearch.words("a b c d e f g h i"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("8 words");
    assertThat(ProductSearch.words("a b c d e f g h")).hasSize(8);
  }
}
