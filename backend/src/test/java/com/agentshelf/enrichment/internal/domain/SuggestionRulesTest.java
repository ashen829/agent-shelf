package com.agentshelf.enrichment.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProductSuggestion.Change;
import com.agentshelf.enrichment.internal.domain.SuggestionRules.Checked;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SuggestionRulesTest {

  private static final ProductSnapshot PIPE =
      new ProductSnapshot(UUID.randomUUID(), 2, "PVC-20", "PVC PIPE 20MM", null, "pipes", "PCS");

  private static Checked check(ProposedChange... proposals) {
    return SuggestionRules.check(PIPE, List.of(proposals));
  }

  @Test
  void acceptsUsefulChangesWithTheOldValue() {
    Checked checked =
        check(
            new ProposedChange("name", "  PVC pipe 20 mm ", "Fixed letter case.", 0.912),
            new ProposedChange("UNIT", "Pcs", "Lower case unit.", 0.5),
            new ProposedChange("category", "Pipes", "Matches your category.", 1),
            new ProposedChange(
                "description", "A 20 mm PVC pipe.\nSold per piece.", "Missing.", 0.5));

    assertThat(checked.dropped()).isZero();
    assertThat(checked.accepted())
        .containsExactly(
            new Change(
                SuggestionField.NAME,
                "PVC PIPE 20MM",
                "PVC pipe 20 mm",
                "Fixed letter case.",
                new BigDecimal("0.91")),
            new Change(
                SuggestionField.UNIT, "PCS", "pcs", "Lower case unit.", new BigDecimal("0.50")),
            new Change(
                SuggestionField.CATEGORY,
                "pipes",
                "Pipes",
                "Matches your category.",
                new BigDecimal("1.00")),
            new Change(
                SuggestionField.DESCRIPTION,
                null,
                "A 20 mm PVC pipe.\nSold per piece.",
                "Missing.",
                new BigDecimal("0.50")));
  }

  @Test
  void dropsFieldsTheModelMayNotChange() {
    assertThat(
            check(
                    new ProposedChange("price", "0", "Cheaper.", 0.9),
                    new ProposedChange("sku", "X", "Shorter.", 0.9),
                    new ProposedChange("stock", "999", "More.", 0.9))
                .dropped())
        .isEqualTo(3);
  }

  @Test
  void dropsValuesThatBreakTheRules() {
    Checked checked =
        check(
            new ProposedChange("name", "PVC PIPE 20MM", "Same as now.", 0.9),
            new ProposedChange("name", "   ", "Blank.", 0.9),
            new ProposedChange("name", "x".repeat(201), "Too long.", 0.9),
            new ProposedChange("name", "Two\nlines", "Line break.", 0.9),
            new ProposedChange("name", "Bell\u0007", "Control character.", 0.9),
            new ProposedChange("unit", "bundle of 12", "Not an allowed unit.", 0.9),
            new ProposedChange("description", "d".repeat(501), "Too long.", 0.9),
            new ProposedChange("category", "Pipes", "", 0.9),
            new ProposedChange("category", "Pipes", "Too sure.", 1.5),
            new ProposedChange("category", "Pipes", "Not a number.", Double.NaN));

    assertThat(checked.accepted()).isEmpty();
    assertThat(checked.dropped()).isEqualTo(10);
  }

  @Test
  void dropsSuggestionsTheModelItselfDoubts() {
    Checked checked =
        check(
            new ProposedChange("category", "Pipes", "Probably pipes.", 0.494),
            new ProposedChange("name", "PVC pipe 20 mm", "Fixed case.", 0.495));

    assertThat(checked.accepted()).extracting(Change::field).containsExactly(SuggestionField.NAME);
    assertThat(checked.dropped()).isOne();
  }

  @Test
  void onlyRespellsOrFillsUnitsAndNeverChangesWhatIsSold() {
    assertThat(unitChange("Nos", "pcs")).isTrue();
    assertThat(unitChange("Nos.", "pcs")).isTrue();
    assertThat(unitChange("PR", "pair")).isTrue();
    assertThat(unitChange("Litres", "l")).isTrue();
    assertThat(unitChange("COIL", "coil")).isTrue();
    assertThat(unitChange(null, "box")).isTrue();
    assertThat(unitChange(" ", "kg")).isTrue();

    assertThat(unitChange("tube", "pcs")).isFalse();
    assertThat(unitChange("tin", "can")).isFalse();
    assertThat(unitChange("COIL", "roll")).isFalse();
    assertThat(unitChange("kg", "g")).isFalse();
    assertThat(unitChange(null, "bundle")).isFalse();
  }

  private static boolean unitChange(@Nullable String current, String suggested) {
    ProductSnapshot product =
        new ProductSnapshot(UUID.randomUUID(), 1, "A", "A", null, null, current);
    return !SuggestionRules.check(
            product, List.of(new ProposedChange("unit", suggested, "Unit.", 0.9)))
        .accepted()
        .isEmpty();
  }

  @Test
  void neverReplacesTheSuppliersOwnDescription() {
    ProductSnapshot described =
        new ProductSnapshot(UUID.randomUUID(), 1, "A", "A", "Supplier text.", null, null);

    Checked checked =
        SuggestionRules.check(
            described, List.of(new ProposedChange("description", "Better text.", "Nicer.", 0.9)));

    assertThat(checked.accepted()).isEmpty();
  }

  @Test
  void keepsOneChangePerFieldAndCleansReasons() {
    Checked checked =
        check(
            new ProposedChange(
                "name", "PVC pipe 20 mm", "Fixed\ncase.\u0000 " + "r".repeat(400), 0.8),
            new ProposedChange("name", "PVC Pipe 20mm", "Second try.", 0.9));

    assertThat(checked.accepted()).hasSize(1);
    assertThat(checked.dropped()).isEqualTo(1);
    String reason = checked.accepted().getFirst().reason();
    assertThat(reason).startsWith("Fixed case. rrr").hasSize(300).doesNotContain("\u0000");
  }
}
