package com.agentshelf.identity.internal.web;

import com.agentshelf.shared.CurrencyCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

class ValidCurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return value == null || CurrencyCode.isValid(value);
  }
}
