# Roadmap

## How we build
- One milestone at a time. Don't start the next one until the current one meets its "Done when".
- Inside a milestone, build small vertical slices: migration, domain, API, UI and tests together.
- Every milestone ends with all checks green, docs updated, a short demo and a git tag
  (`v0.1.0`, `v0.2.0` and so on).
- Ideas that come up mid-milestone go to "Later" below, not into the current milestone.

## M0: Foundation (walking skeleton)
Status: done, 2026-11-09, tagged `v0.1.0`.

Scope:
- Repo structure, Maven backend with Spring Boot and Spring Modulith, Angular app shell
- `infra/docker-compose.yml` with PostgreSQL. Flyway baseline migration.
- CI pipeline: build, format check, lint, NullAway, module verification, tests,
  Playwright smoke test
- Health checks, structured logging, request IDs, global error handling
- `identity` module: supplier signup and login, plus an empty dashboard layout
- ADRs: 0001 modular monolith, 0002 authentication, 0003 background jobs, 0004 UI library

Done when:
- One documented command starts everything locally.
- A supplier can sign up, log in and see the empty dashboard.
- CI is green on the main branch.

## M1: Catalog
Scope:
- Product create, read, update and delete
- CSV import as a background job, with a column-mapping screen and an error report
- Product list with search, filters and pagination
- Excel import as a small follow-up slice (M1.1)

Done when:
- A supplier imports 500 products from a CSV, fixes the reported errors and searches them.
- Tenant isolation tests pass.

## M2: AI cleanup
Scope:
- `ProductEnrichmentModel` port and Claude adapter (Message Batches API, cached prompt)
- Suggestion review screen: approve, reject and bulk approve
- Token and cost tracking per job

Done when:
- Suggestions for 500 products cost under $1.
- Approved suggestions update products. Rejected ones change nothing.

## M3: Rules engine
Scope:
- Quantity price tiers, minimum order value, maximum discount, delivery areas, quote expiry
- Rules editing screens in the dashboard

Done when:
- `PriceCalculator` passes unit tests for every rule and edge case.
- A supplier can edit rules and see example prices update.

## M4: Agent gateway (read-only)
Scope:
- MCP server with tools `search_products`, `get_product`, `get_price`, `check_stock`
- Buyer agent API keys (create, list, revoke) with rate limits
- Logging of every tool call

Done when:
- Claude connects to the MCP server, finds products and gets correct prices.
- Revoked keys and over-limit calls are rejected.

## M5: Quotes
Scope:
- Tools `request_quote` and `get_quote_status`
- Approval inbox: approve, edit or reject quotes
- Email alerts for new quotes and a background job that expires old quotes

Done when:
- An agent requests a quote, the supplier approves it, and the agent sees `APPROVED`.

## M6: Insights, hardening and launch
Scope:
- Insights dashboard: top searches, searches with no results, quotes per agent, approval rate
- Security review and a load test of the MCP tools
- Deployment to staging and production
- Demo data (a fake wholesaler with about 500 messy products) and a 60-second demo video

Done when:
- The app runs in production.
- Search handles 50 requests per second with p95 latency under 300 ms.
- The demo video is recorded.

## Follow-ups from M0 (planned, not forgotten)
- Throttle failed dashboard logins: issue #8 (before launch, uses the M4 rate limiter).
- Email verification, so sign-up stops revealing registered emails (with email sending, M5).
- Self-host the Roboto and Material Symbols fonts, then drop Google Fonts from the CSP
  (ADR 0004, before launch).
- OpenTelemetry tracing (request IDs and JSON logs are in place; add tracing with deployment, M6).

## Later (not now)
ERP connectors (Odoo, SAP), ACP and UCP adapters, payments (AP2), semantic search,
multi-language support, a public discovery feed, a marketplace across suppliers.
