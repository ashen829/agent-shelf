package com.agentshelf.identity.internal.security;

import com.agentshelf.shared.UnauthenticatedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

  static final String BAD_CREDENTIALS = "Email or password is incorrect.";

  private final AuthenticationManager authenticationManager;

  LoginService(AuthenticationManager authenticationManager) {
    this.authenticationManager = authenticationManager;
  }

  /**
   * Checks the password. The same generic error for an unknown email and a wrong password, and
   * Spring Security hashes a dummy password for unknown emails so timing doesn't leak either.
   */
  public SessionUser authenticate(String email, String password) {
    try {
      Authentication result =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
      if (result.getPrincipal() instanceof UserAccount account) {
        return account.toSessionUser();
      }
    } catch (AuthenticationException e) {
      throw new UnauthenticatedException(BAD_CREDENTIALS);
    }
    throw new UnauthenticatedException(BAD_CREDENTIALS);
  }
}
