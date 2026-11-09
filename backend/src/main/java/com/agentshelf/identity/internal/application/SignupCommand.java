package com.agentshelf.identity.internal.application;

import com.agentshelf.shared.CurrencyCode;

/** A validated sign-up request: creates a supplier and its first user (the owner). */
public record SignupCommand(
    String companyName, CurrencyCode currency, String email, String rawPassword) {}
