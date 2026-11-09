package com.agentshelf.shared.internal.web;

import com.agentshelf.shared.ConflictException;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that throw each kind of error. Import it explicitly where needed. */
@TestComponent
@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

  static final String SECRET = "db password is hunter2";

  @GetMapping("/not-found")
  void notFound() {
    throw new NotFoundException("Product 42 was not found.");
  }

  @GetMapping("/conflict")
  void conflict() {
    throw new ConflictException("SKU ABC-1 already exists.");
  }

  @GetMapping("/stale")
  void stale() {
    throw new OptimisticLockingFailureException("version 3 expected, found 4");
  }

  @GetMapping("/rule")
  void rule() {
    throw new DomainException("Quantity is below the minimum order.");
  }

  @GetMapping("/boom")
  void boom() {
    throw new IllegalStateException(SECRET);
  }

  @PostMapping("/validate")
  void validate(@Valid @RequestBody Body body) {}

  record Body(@Nullable @NotBlank String name, @Positive int quantity) {}
}
