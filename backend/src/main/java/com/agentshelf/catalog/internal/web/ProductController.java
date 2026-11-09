package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.application.ProductService;
import com.agentshelf.catalog.internal.application.ProductSort;
import com.agentshelf.catalog.internal.domain.ProductDetails;
import com.agentshelf.catalog.internal.domain.ProductStatus;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.identity.IdentityApi;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.Money;
import com.agentshelf.shared.ProblemResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Products", description = "The signed-in supplier's catalog")
@ApiResponse(
    responseCode = "401",
    description = "Not signed in",
    content =
        @Content(
            mediaType = ProblemResponses.MEDIA_TYPE,
            schema = @Schema(implementation = ProblemDetail.class)))
class ProductController {

  private final ProductService products;
  private final IdentityApi identity;

  ProductController(ProductService products, IdentityApi identity) {
    this.products = products;
    this.identity = identity;
  }

  @Operation(operationId = "listProducts", summary = "One page of products")
  @ApiResponse(responseCode = "200", description = "A page of products")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid page, size or sort",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping
  ProductPage list(
      @RequestParam(defaultValue = "ACTIVE") ProductStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "NAME") ProductSort sort) {
    return ProductPage.from(products.list(user(), status, page, size, sort));
  }

  @Operation(operationId = "getProduct", summary = "One product")
  @ApiResponse(responseCode = "200", description = "The product")
  @ApiResponse(
      responseCode = "404",
      description = "No such product for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  ProductResponse get(@PathVariable UUID id) {
    return ProductResponse.from(products.get(user(), id));
  }

  @Operation(operationId = "createProduct", summary = "Add a product")
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "SKU already used by another product",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ProductResponse create(@Valid @RequestBody ProductRequest body) {
    return ProductResponse.from(products.create(user(), details(body)));
  }

  @Operation(operationId = "updateProduct", summary = "Edit a product")
  @ApiResponse(responseCode = "200", description = "Saved")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No such product for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "SKU already used, or someone else saved this product first",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping("/{id}")
  ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest body) {
    Long version = body.version();
    if (version == null) {
      throw new DomainException("Send the version you loaded when editing a product.");
    }
    return ProductResponse.from(products.update(user(), id, version, details(body)));
  }

  @Operation(
      operationId = "archiveProduct",
      summary = "Archive a product (hidden from lists and agents, history kept)")
  @ApiResponse(responseCode = "204", description = "Archived")
  @ApiResponse(
      responseCode = "404",
      description = "No such product for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void archive(@PathVariable UUID id) {
    products.archive(user(), id);
  }

  @Operation(operationId = "restoreProduct", summary = "Make an archived product active again")
  @ApiResponse(responseCode = "200", description = "Restored")
  @ApiResponse(
      responseCode = "404",
      description = "No such product for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/{id}/restore")
  ProductResponse restore(@PathVariable UUID id) {
    return ProductResponse.from(products.restore(user(), id));
  }

  private CurrentUser user() {
    return identity.currentUser();
  }

  private ProductDetails details(ProductRequest body) {
    return new ProductDetails(
        body.sku(),
        body.name(),
        body.description(),
        body.category(),
        body.unit(),
        new Money(new BigDecimal(body.price()), identity.currentSupplierCurrency()),
        body.stockQty());
  }
}
