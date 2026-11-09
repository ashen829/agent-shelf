package com.agentshelf.identity.internal.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Dashboard security: session cookie (sessions stored in PostgreSQL by Spring Session JDBC), CSRF
 * protection for the Angular app, and errors rendered by the global ProblemDetail handler. See ADR
 * 0002.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository contextRepository,
      CsrfTokenRepository csrfTokens,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors)
      throws Exception {
    return http.authorizeHttpRequests(requests ->
            requests
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/logout")
                .permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                .permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .securityContext(context -> context.securityContextRepository(contextRepository))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(
                    (request, response, e) -> errors.resolveException(request, response, null, e))
                .accessDeniedHandler(
                    (request, response, e) -> errors.resolveException(request, response, null, e)))
        .headers(headers ->
            headers.contentSecurityPolicy(
                csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .build();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new DelegatingSecurityContextRepository(
        new RequestAttributeSecurityContextRepository(),
        new HttpSessionSecurityContextRepository());
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
    return repository;
  }

  /** Delegating encoder: bcrypt today, and stored hashes say which algorithm made them. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService users, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
