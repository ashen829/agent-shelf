package com.agentshelf.enrichment.internal.domain;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProductSuggestion.Change;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Checks what the AI model proposed before anything is saved. The model's output is untrusted:
 * product text can contain instructions, and the model can be wrong. Anything that breaks a rule is
 * dropped (and counted), never "fixed up".
 */
public final class SuggestionRules {

  /** Units the model may suggest. Kept short so units are consistent across a catalog. */
  public static final List<String> UNITS =
      List.of(
          "pcs", "pair", "set", "box", "pack", "case", "pallet", "roll", "coil", "sheet", "bag",
          "bottle", "can", "tin", "tube", "kg", "g", "t", "l", "ml", "m", "cm", "mm", "m2", "m3");

  /**
   * Other spellings of the allowed units. A unit may only be changed to another spelling of the
   * same unit ("Nos" to "pcs"), never to a different one ("tube" to "pcs" changes what is sold).
   */
  static final Map<String, List<String>> UNIT_SPELLINGS =
      Map.ofEntries(
          Map.entry(
              "pcs", List.of("pc", "piece", "pieces", "nos", "no", "ea", "each", "unit", "units")),
          Map.entry("pair", List.of("pr", "prs", "pairs")),
          Map.entry("set", List.of("sets")),
          Map.entry("box", List.of("bx", "boxes")),
          Map.entry("pack", List.of("pk", "pkt", "packet", "packs")),
          Map.entry("case", List.of("cs", "cases")),
          Map.entry("roll", List.of("rl", "rolls")),
          Map.entry("coil", List.of("coils")),
          Map.entry("sheet", List.of("sht", "sheets")),
          Map.entry("bag", List.of("bags")),
          Map.entry("bottle", List.of("btl", "bottles")),
          Map.entry("tube", List.of("tubes")),
          Map.entry("kg", List.of("kgs", "kilo", "kilogram", "kilograms")),
          Map.entry("l", List.of("ltr", "lt", "litre", "litres", "liter", "liters")),
          Map.entry("ml", List.of("mls")),
          Map.entry("m", List.of("mtr", "mtrs", "metre", "metres", "meter", "meters")),
          Map.entry("m2", List.of("sqm", "sq m", "m²")),
          Map.entry("m3", List.of("cbm", "cu m", "m³")));

  /** Below this, the model itself isn't sure: such suggestions only waste the supplier's time. */
  static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.50");

  static final int MAX_REASON_LENGTH = 300;

  /** Control characters, except line breaks (allowed in descriptions only). */
  private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

  private SuggestionRules() {}

  /** The accepted changes, and how many proposals were dropped. */
  public record Checked(List<Change> accepted, int dropped) {}

  public static Checked check(ProductSnapshot product, List<ProposedChange> proposals) {
    List<Change> accepted = new ArrayList<>();
    Set<SuggestionField> seen = EnumSet.noneOf(SuggestionField.class);
    int dropped = 0;
    for (ProposedChange proposal : proposals) {
      Optional<Change> change = check(product, proposal);
      if (change.isPresent() && seen.add(change.get().field())) {
        accepted.add(change.get());
      } else {
        dropped++;
      }
    }
    return new Checked(List.copyOf(accepted), dropped);
  }

  private static Optional<Change> check(ProductSnapshot product, ProposedChange proposal) {
    Optional<SuggestionField> maybeField = SuggestionField.fromModel(proposal.field());
    if (maybeField.isEmpty()) {
      return Optional.empty();
    }
    SuggestionField field = maybeField.get();
    String value = proposal.value().strip();
    if (field == SuggestionField.UNIT) {
      value = value.toLowerCase(Locale.ROOT);
    }
    String reason = clean(proposal.reason());
    double confidence = proposal.confidence();
    @Nullable String oldValue = current(product, field);

    boolean valid =
        !value.isEmpty()
            && value.length() <= field.maxLength()
            && !CONTROL.matcher(value).find()
            && (field == SuggestionField.DESCRIPTION || value.indexOf('\n') < 0)
            && (field != SuggestionField.UNIT || sameUnit(oldValue, value))
            // Descriptions are only written where there is none: never replace the supplier's text.
            && (field != SuggestionField.DESCRIPTION || oldValue == null || oldValue.isBlank())
            && !value.equals(oldValue)
            && !reason.isEmpty()
            && confidence >= 0
            && confidence <= 1;
    if (!valid) {
      return Optional.empty();
    }
    BigDecimal rounded = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
    if (rounded.compareTo(MIN_CONFIDENCE) < 0) {
      return Optional.empty();
    }
    return Optional.of(new Change(field, oldValue, value, reason, rounded));
  }

  /** An allowed unit that fills a missing unit or respells the current one. */
  private static boolean sameUnit(@Nullable String current, String suggested) {
    if (!UNITS.contains(suggested)) {
      return false;
    }
    if (current == null || current.isBlank()) {
      return true;
    }
    String now = current.strip().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    return now.equals(suggested) || UNIT_SPELLINGS.getOrDefault(suggested, List.of()).contains(now);
  }

  private static @Nullable String current(ProductSnapshot product, SuggestionField field) {
    return switch (field) {
      case NAME -> product.name();
      case CATEGORY -> product.category();
      case UNIT -> product.unit();
      case DESCRIPTION -> product.description();
    };
  }

  /** Reasons are shown to the supplier: one line, no control characters, at most 300 chars. */
  private static String clean(String reason) {
    String oneLine = reason.replaceAll("\\s+", " ").strip();
    String plain = CONTROL.matcher(oneLine).replaceAll("");
    return plain.length() > MAX_REASON_LENGTH ? plain.substring(0, MAX_REASON_LENGTH) : plain;
  }
}
