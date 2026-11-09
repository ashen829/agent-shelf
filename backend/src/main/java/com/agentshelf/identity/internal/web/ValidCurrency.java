package com.agentshelf.identity.internal.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An ISO 4217 currency code in upper case, for example {@code EUR}. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCurrencyValidator.class)
@interface ValidCurrency {
  String message() default "must be an ISO 4217 currency code, for example EUR";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
