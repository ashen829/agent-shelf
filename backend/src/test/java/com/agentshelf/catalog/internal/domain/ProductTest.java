package com.agentshelf.catalog.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

  private static final Money PRICE = new Money(new BigDecimal("4.50"), new CurrencyCode("LKR"));

  private static ProductDetails details(String sku) {
    return new ProductDetails(sku, "PVC pipe 20 mm", null, "Pipes", "m", PRICE, 100);
  }

  @Test
  void trimsTextAndTurnsBlankOptionalFieldsIntoNothing() {
    ProductDetails d =
        new ProductDetails("  PVC-20 ", "  PVC pipe  ", "   ", " Pipes ", "", PRICE, 0);

    assertThat(d.sku()).isEqualTo("PVC-20");
    assertThat(d.name()).isEqualTo("PVC pipe");
    assertThat(d.description()).isNull();
    assertThat(d.category()).isEqualTo("Pipes");
    assertThat(d.unit()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PVC-20", "a", "ABC.123/x_y-z", "9"})
  void acceptsValidSkus(String sku) {
    assertThat(details(sku).sku()).isEqualTo(sku);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "PVC 20", "-PVC", "PVC#20", "ಎ123"})
  void rejectsInvalidSkus(String sku) {
    assertThatThrownBy(() -> details(sku)).isInstanceOf(DomainException.class);
  }

  @Test
  void rejectsTooLongSkuAndNegativeStock() {
    assertThatThrownBy(() -> details("A".repeat(65))).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> new ProductDetails("A", "B", null, null, null, PRICE, -1))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void startsActiveAndCanBeArchivedAndRestored() {
    Product product = new Product(UUID.randomUUID(), details("PVC-20"), Instant.now());
    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);

    product.archive();
    assertThat(product.status()).isEqualTo(ProductStatus.ARCHIVED);

    product.restore();
    assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
  }

  @Test
  void currencyCannotChange() {
    Product product = new Product(UUID.randomUUID(), details("PVC-20"), Instant.now());
    Money dollars = new Money(new BigDecimal("1"), new CurrencyCode("USD"));

    assertThatThrownBy(() ->
            product.update(new ProductDetails("PVC-20", "Pipe", null, null, null, dollars, 1)))
        .isInstanceOf(DomainException.class);
  }
}
