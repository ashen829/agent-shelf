package com.agentshelf.identity.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record SignupRequest(
    @NotBlank @Size(min = 2, max = 100, message = "must be 2 to 100 characters") String companyName,
    @NotBlank @ValidCurrency String currency,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 64, message = "must be 12 to 64 characters") @MaxUtf8Bytes(72)
        String password) {}
