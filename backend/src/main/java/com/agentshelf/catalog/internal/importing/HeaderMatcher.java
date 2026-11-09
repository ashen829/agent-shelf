package com.agentshelf.catalog.internal.importing;

import com.agentshelf.catalog.internal.domain.ColumnMapping;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Suggests which column holds which product field, from common header names. */
public final class HeaderMatcher {

  private static final List<String> SKU =
      List.of(
          "sku",
          "itemcode",
          "productcode",
          "code",
          "partno",
          "partnumber",
          "articleno",
          "article",
          "ref");
  private static final List<String> NAME =
      List.of("name", "productname", "itemname", "item", "product", "title");
  private static final List<String> DESCRIPTION = List.of("description", "desc", "details");
  private static final List<String> CATEGORY =
      List.of("category", "productgroup", "group", "type", "family");
  private static final List<String> UNIT = List.of("unit", "uom", "unitofmeasure", "units", "per");
  private static final List<String> PRICE =
      List.of("price", "unitprice", "sellingprice", "wholesaleprice", "rate", "listprice");
  private static final List<String> STOCK =
      List.of("stock", "stockqty", "qty", "quantity", "onhand", "available", "inventory");

  private HeaderMatcher() {}

  public static ColumnMapping suggest(List<String> headers) {
    Set<Integer> used = new HashSet<>();
    return new ColumnMapping(
        find(headers, SKU, used),
        find(headers, NAME, used),
        find(headers, DESCRIPTION, used),
        find(headers, CATEGORY, used),
        find(headers, UNIT, used),
        find(headers, PRICE, used),
        find(headers, STOCK, used));
  }

  /** Exact names first (in order of preference), so "Item code" wins over "Item". */
  private static @Nullable Integer find(
      List<String> headers, List<String> names, Set<Integer> used) {
    for (String name : names) {
      for (int i = 0; i < headers.size(); i++) {
        if (!used.contains(i) && normalize(headers.get(i)).equals(name)) {
          used.add(i);
          return i;
        }
      }
    }
    return null;
  }

  private static String normalize(String header) {
    return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
