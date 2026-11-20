package com.agentshelf.enrichment.internal.domain;

import com.agentshelf.catalog.ProductSnapshot;
import com.agentshelf.enrichment.internal.domain.ProductSuggestion.Change;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
          "pcs", "pair", "set", "box", "pack", "case", "pallet", "roll", "sheet", "bag", "bottle",
          "can", "kg", "g", "t", "l", "ml", "m", "cm", "mm", "m2", "m3");

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
            && (field != SuggestionField.UNIT || UNITS.contains(value))
            // Descriptions are only written where there is none: never replace the supplier's text.
            && (field != SuggestionField.DESCRIPTION || oldValue == null || oldValue.isBlank())
            && !value.equals(oldValue)
            && !reason.isEmpty()
            && confidence >= 0
            && confidence <= 1;
    if (!valid) {
      return Optional.empty();
    }
    return Optional.of(
        new Change(
            field,
            oldValue,
            value,
            reason,
            BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP)));
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
