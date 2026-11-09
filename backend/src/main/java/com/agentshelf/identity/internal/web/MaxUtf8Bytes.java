package com.agentshelf.identity.internal.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** At most {@link #value()} bytes in UTF-8. bcrypt only uses the first 72 bytes of a password. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@interface MaxUtf8Bytes {
  int value();

  String message() default "is too long";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
