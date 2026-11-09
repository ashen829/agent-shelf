package com.agentshelf.identity.internal.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

  private int max;

  @Override
  public void initialize(MaxUtf8Bytes annotation) {
    max = annotation.value();
  }

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return fits(value, max);
  }

  /** Null is left to {@code @NotBlank}. */
  static boolean fits(@Nullable String value, int maxBytes) {
    return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
  }
}
