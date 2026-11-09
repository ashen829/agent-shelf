package com.agentshelf.identity.internal.application;

import com.agentshelf.identity.Role;
import com.agentshelf.identity.internal.domain.AppUser;
import com.agentshelf.identity.internal.domain.EmailAddress;
import com.agentshelf.identity.internal.domain.Slugs;
import com.agentshelf.identity.internal.domain.Supplier;
import com.agentshelf.identity.internal.persistence.AppUserRepository;
import com.agentshelf.identity.internal.persistence.SupplierRepository;
import com.agentshelf.identity.internal.security.SessionUser;
import com.agentshelf.shared.ConflictException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

  static final String EMAIL_TAKEN = "An account with this email already exists.";
  private static final int SLUG_ATTEMPTS = 5;

  private final SupplierRepository suppliers;
  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;

  SignupService(
      SupplierRepository suppliers, AppUserRepository users, PasswordEncoder passwordEncoder) {
    this.suppliers = suppliers;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /** Creates the supplier and its owner in one transaction. */
  @Transactional
  public SessionUser signup(SignupCommand command) {
    String email = EmailAddress.normalize(command.email());
    if (users.existsByEmail(email)) {
      throw new ConflictException(EMAIL_TAKEN);
    }
    Instant now = Instant.now();
    Supplier supplier =
        new Supplier(
            command.companyName(), uniqueSlug(command.companyName()), command.currency(), now);
    AppUser owner =
        new AppUser(
            supplier.id(), email, passwordEncoder.encode(command.rawPassword()), Role.OWNER, now);
    try {
      suppliers.saveAndFlush(supplier);
      users.saveAndFlush(owner);
    } catch (DataIntegrityViolationException e) {
      // Two sign-ups with the same email at the same moment: the unique constraint decides.
      if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("app_user_email_uk")) {
        throw new ConflictException(EMAIL_TAKEN);
      }
      throw e;
    }
    return new SessionUser(owner.id(), supplier.id(), owner.email(), owner.role());
  }

  private String uniqueSlug(String companyName) {
    String base = Slugs.fromName(companyName);
    String slug = base;
    for (int attempt = 0; attempt < SLUG_ATTEMPTS && suppliers.existsBySlug(slug); attempt++) {
      slug = Slugs.withRandomSuffix(base);
    }
    return slug;
  }
}
