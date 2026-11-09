# Architecture

## Style: modular monolith
One deployable backend, split into modules with boundaries enforced by Spring Modulith.

- Simple to run, test and debug today.
- Clean boundaries keep the code maintainable as it grows.
- It scales by running more identical instances behind a load balancer.
- A module can be extracted into its own service later without a rewrite.

## Backend modules (`com.agentshelf.<module>`)
| Module | Owns | Public API (used by other modules) |
|---|---|---|
| `identity` | Suppliers, users, login, roles, tenant context | Current supplier and user |
| `catalog` | Products and import jobs | Product queries; `ProductsImported` event |
| `enrichment` | AI cleanup jobs and suggestions | `SuggestionsReady` event |
| `rules` | Price tiers, discounts, minimum order, delivery areas | `PriceCalculator` |
| `quotes` | Quote requests and the approval workflow | Quote commands; `QuoteRequested`, `QuoteApproved` events |
| `agentgateway` | MCP server, buyer agent API keys, rate limits | None (edge module) |
| `notifications` | Email | None (listens to events) |
| `insights` | Agent activity and demand gaps | Insight queries (listens to events) |
| `shared` | Tiny shared kernel: `Money`, ID types, error types | Keep it small |

Dependency direction (no cycles):
- `agentgateway` uses `catalog`, `rules` and `quotes`.
- `quotes` uses `catalog` and `rules`.
- `notifications` and `insights` only listen to events.
- Every module may use `identity` for tenant context and `shared`.

## Inside a module
```text
catalog/
  CatalogApi.java            public facade: the only class other modules call
  ProductsImported.java      public events
  internal/
    domain/                  entities, value objects, business logic
    application/             use cases and transactions
    persistence/             repositories and JPA mappings
    web/                     REST controllers and DTOs
```
- Classes in the module root are public. Everything under `internal/` is private to the module.
- Controllers stay thin: validate, call an application service, map the result to a DTO.
- Business logic lives in domain classes, not in controllers or repositories.
- Refer to another module's data by ID only. Never share entities across modules.
- Side effects in other modules use events with `@ApplicationModuleListener`. The Event
  Publication Registry stores events in the database so none are lost after a crash.

## Data
- PostgreSQL. Each module owns its tables in its own schema (for example `catalog.product`).
- No cross-module joins and no foreign keys across modules. Reference by ID.
- UUID primary keys (time-ordered). Every table has `created_at`, `updated_at` and `version`.
- Flyway migrations. Never edit a migration that has already run.
- Tenant isolation: `supplier_id` on every tenant table, enforced in every repository query.
  PostgreSQL row-level security can be added later as extra protection (decide in an ADR).

## Background jobs
- Imports, AI cleanup and scheduled work (such as quote expiry) run as background jobs with
  db-scheduler on PostgreSQL. Details in `docs/adr/0003-background-jobs.md`.
- Instances claim work with `SELECT ... FOR UPDATE SKIP LOCKED` (db-scheduler's lock-and-fetch),
  which stays safe with many instances running.
- db-scheduler decides when and where a job runs; each module's own job table (for example
  `catalog.import_job`) holds the status, counts and errors that suppliers see.
- Jobs are idempotent and retry with backoff. Failures are recorded and visible in the dashboard.

## Agent gateway (MCP)
- MCP server built with Spring AI, Streamable HTTP transport, at `/mcp`.
- Auth: the buyer agent sends an API key in a header. Keys are stored hashed and map to one supplier.
- Rate limit per key with Bucket4j backed by PostgreSQL, so limits hold across instances.
- Tools are thin adapters over the `catalog`, `rules` and `quotes` public APIs.
- New agent protocols (ACP, UCP) are added later as new adapters in this module only.
  Core modules don't change.
- Every tool call publishes an event that `insights` stores.

## AI provider
- `enrichment` defines a port (`ProductEnrichmentModel`). The Claude adapter implements it
  with the Message Batches API and prompt caching. Another provider or a local model can
  replace it without touching the rest of the module.
- Validate the AI's JSON output against a schema. Reject anything invalid.
- Track tokens and cost per job.

## Security
- Dashboard login with Spring Security. Use httpOnly, secure session cookies with CSRF
  protection. Never store tokens in localStorage.
- Sessions are stored in PostgreSQL (Spring Session JDBC) so the app stays stateless.
  Details in `docs/adr/0002-authentication.md`.
- Roles: `OWNER` and `STAFF`.
- The API contract is committed at `docs/api/openapi.json` and checked against the code in
  every build (`OpenApiContractIT`).
- Validate every input. Lock CORS to the frontend origin and set security headers.
- Secrets come only from the environment. Never log secrets, API keys or personal data.
- CI scans dependencies for known vulnerabilities.

## Observability
- Health: `/actuator/health` with liveness and readiness probes.
- Metrics (Micrometer): HTTP latency, job durations, MCP calls per tool, AI token usage.
- Tracing: OpenTelemetry, with the request ID in every log line.
- Logs: structured JSON in `prod`, readable text in `local`.

## Frontend (Angular)
```text
frontend/src/app/
  core/       auth, HTTP interceptors, error handling, layout
  shared/     reusable UI components and pipes
  features/   catalog, review, rules, quotes, insights (lazy-loaded routes)
  api/        generated OpenAPI client (never edit by hand)
```
- Standalone components, signals for state, `OnPush` change detection.
- A feature never imports another feature's internals. Shared code goes in `shared/` or `core/`.
- Strict TypeScript with no `any`.
- Choose the UI component library in an ADR during M0.

## Scaling path (only when metrics show the need)
1. Run two or more backend instances behind a load balancer. This works from day one.
2. Add connection pooling (PgBouncer) and a read replica for heavy queries.
3. Add Redis for caching and rate limits if PostgreSQL becomes the bottleneck.
4. Run job workers as a separate deployment of the same code.
5. Extract a module into its own service only when it clearly needs independent scaling.

## Deployment
- Docker images for the backend and the frontend (served by nginx).
- Environments: `local` (Docker Compose), `staging` and `prod`.
- CI builds, tests and publishes images. Choose the hosting target in an ADR during M6.
