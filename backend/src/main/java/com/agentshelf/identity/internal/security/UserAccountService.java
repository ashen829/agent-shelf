package com.agentshelf.identity.internal.security;

import com.agentshelf.identity.internal.domain.EmailAddress;
import com.agentshelf.identity.internal.persistence.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserAccountService implements UserDetailsService {

  private final AppUserRepository users;

  UserAccountService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) {
    return users
        .findByEmail(EmailAddress.normalize(email))
        .map(u -> new UserAccount(u.id(), u.supplierId(), u.email(), u.role(), u.passwordHash()))
        .orElseThrow(() -> new UsernameNotFoundException("No such user"));
  }
}
