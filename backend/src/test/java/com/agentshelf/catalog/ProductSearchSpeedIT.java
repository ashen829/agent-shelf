package com.agentshelf.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.TestcontainersConfiguration;
import com.agentshelf.shared.Ids;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 20,000 products for one supplier (and noise from another): search uses the trigram index and
 * stays fast. The same search will serve AI agents in M4 (target: p95 under 300 ms).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSearchSpeedIT {

  private static final UUID SUPPLIER = Ids.newId();
  private static final UUID OTHER = Ids.newId();

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CatalogApi catalog;

  @BeforeAll
  void manyProducts() {
    seed(SUPPLIER, 20_000);
    seed(OTHER, 5_000);
    jdbc.execute("ANALYZE catalog.product");
  }

  /**
   * At 25,000 rows a full scan (or the supplier index) is still cheap, so PostgreSQL may rightly
   * choose it. What must hold is that the search condition CAN use the trigram index (query and
   * index match), so the planner switches to it as catalogs grow. With sequential scans discouraged
   * and only the search condition, the trigram index is the one that can apply.
   */
  @Test
  void searchCanUseTheTrigramIndex() {
    String plan =
        jdbc.execute((java.sql.Connection connection) -> {
          connection.setAutoCommit(false);
          try (var discourage = connection.createStatement();
              var explain =
                  connection.prepareStatement(
                      "EXPLAIN SELECT id FROM catalog.product"
                          + " WHERE search_text LIKE '%flange 400%'")) {
            discourage.execute("SET LOCAL enable_seqscan = off");
            StringBuilder lines = new StringBuilder();
            try (var rows = explain.executeQuery()) {
              while (rows.next()) {
                lines.append(rows.getString(1)).append('\n');
              }
            }
            return lines.toString();
          } finally {
            connection.rollback();
            connection.setAutoCommit(true);
          }
        });

    assertThat(plan).contains("product_search_trgm_ix");
  }

  @Test
  void searchIsFastAndOnlyReturnsTheSuppliersProducts() {
    catalog.searchProducts(SUPPLIER, "warm up", 0, 20);
    List<Long> millis = new ArrayList<>();
    for (String text :
        new String[] {"flange 400", "valve 12", "pipe", "elbow 90", "nothing-here"}) {
      long start = System.nanoTime();
      var page = catalog.searchProducts(SUPPLIER, text, 0, 20);
      millis.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
      assertThat(page.getContent()).allSatisfy(p -> assertThat(p.sku()).startsWith("S1-"));
    }
    millis.sort(Long::compare);

    assertThat(catalog.searchProducts(SUPPLIER, "flange 400", 0, 20).getTotalElements())
        .isPositive();
    assertThat(millis.get(millis.size() / 2)).as("median ms of %s", millis).isLessThan(300);
  }

  private void seed(UUID supplier, int count) {
    String prefix = supplier.equals(SUPPLIER) ? "S1-" : "S2-";
    jdbc.update(
        """
        INSERT INTO catalog.product (id, supplier_id, sku, name, category, unit, price_amount,
            price_currency, stock_qty, status, created_at, updated_at, version)
        SELECT gen_random_uuid(), ?, ? || n,
               (ARRAY['Steel flange','PVC pipe','Brass valve','Copper elbow','Hex bolt'])[1 + n % 5]
                   || ' ' || (n % 997),
               (ARRAY['Fittings','Pipes','Valves','Fasteners'])[1 + n % 4], 'pcs',
               (n % 5000) + 0.5, 'USD', n % 300, 'ACTIVE', now(), now(), 0
        FROM generate_series(1, ?) AS n
        """,
        supplier, prefix, count);
  }
}
