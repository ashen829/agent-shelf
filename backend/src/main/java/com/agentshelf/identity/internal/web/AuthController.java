package com.agentshelf.identity.internal.web;

import com.agentshelf.identity.CurrentUser;
import com.agentshelf.identity.IdentityApi;
import com.agentshelf.identity.internal.application.AccountQueries;
import com.agentshelf.identity.internal.application.SignupCommand;
import com.agentshelf.identity.internal.application.SignupService;
import com.agentshelf.identity.internal.security.LoginService;
import com.agentshelf.identity.internal.security.SessionManager;
import com.agentshelf.identity.internal.security.SessionUser;
import com.agentshelf.shared.CurrencyCode;
import com.agentshelf.shared.ProblemResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Supplier sign-up, login and the current account")
class AuthController {

  private final SignupService signupService;
  private final LoginService loginService;
  private final SessionManager sessions;
  private final AccountQueries accounts;
  private final IdentityApi identity;

  AuthController(
      SignupService signupService,
      LoginService loginService,
      SessionManager sessions,
      AccountQueries accounts,
      IdentityApi identity) {
    this.signupService = signupService;
    this.loginService = loginService;
    this.sessions = sessions;
    this.accounts = accounts;
    this.identity = identity;
  }

  @Operation(
      operationId = "signup",
      summary = "Create a supplier account and its owner, and sign in")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF token",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Email already registered",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "201", description = "Account created and signed in")
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  AccountResponse signup(
      @Valid @RequestBody SignupRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    SessionUser user =
        signupService.signup(
            new SignupCommand(
                body.companyName(),
                new CurrencyCode(body.currency()),
                body.email(),
                body.password()));
    sessions.signIn(user, request, response);
    return AccountResponse.from(accounts.account(toCurrentUser(user)));
  }

  @Operation(operationId = "login", summary = "Sign in with email and password")
  @ApiResponse(responseCode = "200", description = "Signed in")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid fields",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Email or password is incorrect",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF token",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/login")
  AccountResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    SessionUser user = loginService.authenticate(body.email(), body.password());
    sessions.signIn(user, request, response);
    return AccountResponse.from(accounts.account(toCurrentUser(user)));
  }

  @Operation(operationId = "logout", summary = "Sign out and end the session")
  @ApiResponse(
      responseCode = "403",
      description = "Missing or invalid CSRF token",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "204", description = "Signed out (also when not signed in)")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletRequest request, HttpServletResponse response) {
    sessions.signOut(request, response);
  }

  @Operation(operationId = "me", summary = "The signed-in user and their supplier")
  @ApiResponse(responseCode = "200", description = "The current account")
  @ApiResponse(
      responseCode = "401",
      description = "Not signed in",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/me")
  AccountResponse me() {
    return AccountResponse.from(accounts.account(identity.currentUser()));
  }

  private static CurrentUser toCurrentUser(SessionUser user) {
    return new CurrentUser(user.userId(), user.supplierId(), user.role());
  }
}
