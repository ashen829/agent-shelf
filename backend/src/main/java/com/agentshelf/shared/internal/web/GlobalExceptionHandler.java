package com.agentshelf.shared.internal.web;

import com.agentshelf.shared.ConflictException;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.NotFoundException;
import com.agentshelf.shared.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The one place that turns exceptions into RFC 9457 {@link ProblemDetail} responses. Every body
 * carries the {@code requestId}. Unexpected errors return a generic message; details stay in the
 * server log.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  static final String STALE_MESSAGE =
      "This record was changed by someone else. Reload it and try again.";
  static final String UNEXPECTED_MESSAGE =
      "An unexpected error occurred. Quote the request ID if you contact support.";
  static final String AUTHENTICATION_REQUIRED = "Authentication required.";
  static final String ACCESS_DENIED = "You do not have access to this resource.";
  static final String CSRF_REJECTED = "The request was rejected: missing or invalid CSRF token.";
  static final String NOT_FOUND = "The requested resource does not exist.";

  @ExceptionHandler(UnauthenticatedException.class)
  ProblemDetail unauthenticated(UnauthenticatedException ex) {
    return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  /** Raised by Spring Security when a protected endpoint is called without a session. */
  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail authenticationRequired() {
    return problem(HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED);
  }

  @ExceptionHandler(CsrfException.class)
  ProblemDetail csrfRejected() {
    return problem(HttpStatus.FORBIDDEN, CSRF_REJECTED);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail accessDenied() {
    return problem(HttpStatus.FORBIDDEN, ACCESS_DENIED);
  }

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(ConflictException ex) {
    return problem(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ProblemDetail staleUpdate() {
    return problem(HttpStatus.CONFLICT, STALE_MESSAGE);
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail businessRule(DomainException ex) {
    return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_MESSAGE);
  }

  /** Bean validation failures on a request body: list each field error. */
  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error ->
                Map.of(
                    "field",
                    error.getField(),
                    "message",
                    error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
            .toList();
    ProblemDetail body = ex.getBody();
    body.setDetail("The request has invalid fields.");
    body.setProperty("errors", errors);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /** Unknown routes: a neutral message that doesn't describe how routing works. */
  @Override
  protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = ex.getBody();
    body.setDetail(NOT_FOUND);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /** Adds the request ID to every problem built by Spring's built-in handlers. */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      withRequestId(problem);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private static ProblemDetail problem(HttpStatus status, @Nullable String detail) {
    return withRequestId(ProblemDetail.forStatusAndDetail(status, detail));
  }

  private static ProblemDetail withRequestId(ProblemDetail problem) {
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null) {
      problem.setProperty(RequestIdFilter.MDC_KEY, requestId);
    }
    return problem;
  }
}
