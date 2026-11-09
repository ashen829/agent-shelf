package com.agentshelf.identity.internal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Starts and ends a signed-in session for the JSON login, sign-up and logout endpoints. */
@Component
public class SessionManager {

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();
  private final SessionAuthenticationStrategy sessionFixation =
      new ChangeSessionIdAuthenticationStrategy();
  private final SecurityContextRepository contextRepository;
  private final CsrfTokenRepository csrfTokens;
  private final CsrfAuthenticationStrategy csrfRotation;

  SessionManager(SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokens) {
    this.contextRepository = contextRepository;
    this.csrfTokens = csrfTokens;
    this.csrfRotation = new CsrfAuthenticationStrategy(csrfTokens);
  }

  /** Signs the user in: new session ID (no session fixation), new CSRF token, saved context. */
  public void signIn(SessionUser user, HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
    request.getSession(true);
    sessionFixation.onAuthentication(authentication, request, response);
    csrfRotation.onAuthentication(authentication, request, response);
    if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
      token.getToken();
    }
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
  }

  /** Signs the user out: invalidates the session and clears the CSRF cookie. */
  public void signOut(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler()
        .logout(request, response, contextHolder.getContext().getAuthentication());
    csrfTokens.saveToken(null, request, response);
  }
}
