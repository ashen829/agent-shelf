# 0002: Dashboard authentication — server sessions in PostgreSQL

- Status: Accepted
- Date: 2026-11-09

## Context
Suppliers sign in to the Angular dashboard with email and password. The backend must stay
stateless so several instances can run behind a load balancer. Tokens must never be stored
in `localStorage` (readable by any script after an XSS bug). Buyer AI agents authenticate
separately with API keys (M4); this ADR covers people using the dashboard.

## Decision
- **Server-side sessions** with Spring Security 7. The session lives in PostgreSQL
  (Spring Session JDBC, tables `identity.spring_session*`), so any instance can serve any
  request. Cookie `AGENTSHELF_SESSION`: `HttpOnly`, `SameSite=Lax`, `Secure` in `prod`,
  8 hours idle timeout.
- **JSON endpoints** under `/api/v1/auth` (`signup`, `login`, `logout`, `me`) instead of
  form login, so the API is documented in OpenAPI and the client is generated.
- **CSRF protection** with Spring Security's SPA mode: a readable `XSRF-TOKEN` cookie that
  Angular's `HttpClient` sends back in the `X-XSRF-TOKEN` header. A new token is issued at
  sign-in and at sign-out (the app stays on the page after logout and may post the login
  form next, so it must never be left without one). The frontend and API share one origin (dev proxy now, nginx later), so CORS stays
  closed.
- **Session fixation**: the session ID changes at sign-in.
- **Passwords**: Spring's delegating encoder (bcrypt today; each hash records its algorithm,
  so we can move to Argon2 later without a migration). Length 12–64 characters and at most
  72 bytes, because bcrypt ignores anything longer.
- **Generic login errors**: "Email or password is incorrect." for both an unknown email and a
  wrong password; Spring Security hashes a dummy password for unknown emails, so timing does
  not leak either.
- **Tenant context**: the session principal holds `userId`, `supplierId` and `role`. Other
  modules read it through `IdentityApi.currentUser()` and scope every query by `supplierId`.
- **Errors**: 401 and 403 from the security filters go through the global `ProblemDetail`
  handler, like every other error.

## Options considered
- JWT in `localStorage`: stateless, but readable by injected scripts and impossible to revoke
  before expiry. Rejected.
- JWT in an `HttpOnly` cookie: avoids script access, but still needs a deny-list for logout
  and role changes, which brings back server state. More moving parts than sessions.
- Server sessions in memory: simplest, but breaks with more than one instance. Rejected.
- Server sessions in Redis: fast, but adds infrastructure we don't need yet (see the scaling
  path in `ARCHITECTURE.md`). Spring Session can switch stores without code changes.
- Form login (`formLogin()`): less code, but not a JSON API and not in the OpenAPI contract.

## Consequences
- Easier: logout and revocation are immediate (delete the session row); nothing secret is
  readable by JavaScript; horizontal scaling works from day one.
- Harder: every request reads the session from PostgreSQL. Fine at our scale; Redis is the
  documented next step if metrics show the need. Expired sessions are cleaned up every minute.
- Sessions are stored with Java serialization. Changing `SessionUser` signs everybody out on
  the next deploy; acceptable for now.
- Known gaps, accepted for M0:
  - Sign-up reveals that an email is already registered (409). Fixing this properly needs
    email verification, which needs email sending (M5).
  - Failed logins are not throttled yet:
    [#8](https://github.com/ashen829/agent-shelf/issues/8).
