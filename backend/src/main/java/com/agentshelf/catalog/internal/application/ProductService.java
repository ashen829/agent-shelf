package com.agentshelf.catalog.internal.application;

import com.agentshelf.catalog.internal.domain.Product;
import com.agentshelf.catalog.internal.domain.ProductDetails;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import com.agentshelf.catalog.internal.persistence.ProductRepository;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.shared.ConflictException;
import com.agentshelf.shared.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Product use cases. Every method is scoped to the current user's supplier. */
@Service
public class ProductService {

  static final String NOT_FOUND = "Product not found.";

  private final ProductRepository products;

  ProductService(ProductRepository products) {
    this.products = products;
  }

  @Transactional(readOnly = true)
  public Page<Product> list(
      CurrentUser user, ProductStatus status, int page, int size, ProductSort sort) {
    return products.findBySupplierIdAndStatus(
        user.supplierId(), status, PageRequest.of(page, size, sort.sort()));
  }

  @Transactional(readOnly = true)
  public Product get(CurrentUser user, UUID id) {
    return find(user, id);
  }

  @Transactional
  public Product create(CurrentUser user, ProductDetails details) {
    requireFreeSku(user, details.sku(), null);
    return save(new Product(user.supplierId(), details, Instant.now()));
  }

  /**
   * Replaces the product's fields. {@code expectedVersion} is the version the user edited; if
   * someone saved in between, nothing changes and the caller gets a conflict.
   */
  @Transactional
  public Product update(CurrentUser user, UUID id, long expectedVersion, ProductDetails details) {
    Product product = find(user, id);
    if (product.version() != expectedVersion) {
      throw new OptimisticLockingFailureException("Stale product version");
    }
    requireFreeSku(user, details.sku(), id);
    product.update(details);
    return save(product);
  }

  @Transactional
  public void archive(CurrentUser user, UUID id) {
    find(user, id).archive();
  }

  @Transactional
  public Product restore(CurrentUser user, UUID id) {
    Product product = find(user, id);
    product.restore();
    return save(product);
  }

  private Product find(CurrentUser user, UUID id) {
    return products
        .findByIdAndSupplierId(id, user.supplierId())
        .orElseThrow(() -> new NotFoundException(NOT_FOUND));
  }

  private void requireFreeSku(CurrentUser user, String sku, @Nullable UUID ownId) {
    if (products.skuTaken(user.supplierId(), sku, ownId == null ? NONE : ownId)) {
      throw skuTaken(sku);
    }
  }

  private Product save(Product product) {
    try {
      return products.saveAndFlush(product);
    } catch (DataIntegrityViolationException e) {
      // Two saves with the same SKU at the same moment: the unique index decides.
      if (String.valueOf(e.getMostSpecificCause().getMessage())
          .contains("product_supplier_sku_uk")) {
        throw skuTaken(product.sku());
      }
      throw e;
    }
  }

  private static ConflictException skuTaken(String sku) {
    return new ConflictException("Another product already uses SKU " + sku + ".", "sku");
  }

  private static final UUID NONE = new UUID(0, 0);
}
