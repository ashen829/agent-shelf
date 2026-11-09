# AgentShelf

AgentShelf helps small B2B suppliers (wholesalers) sell to AI buying agents.

Suppliers import their price list, approve AI-suggested data fixes, set their business rules,
and AgentShelf exposes the catalog to AI agents through an MCP server. Agents search products,
get prices and request quotes; the supplier approves every quote. No payments and no automatic
orders.

**Status:** milestone M0 (foundation) is done — a supplier can sign up, log in and see an empty
dashboard. Next up is M1, the catalog. See [the roadmap](docs/ROADMAP.md).

## Quick start

You need Docker. Then, once:

```bash
cp infra/.env.example infra/.env   # then set POSTGRES_PASSWORD in infra/.env
```

Start everything (database, backend, frontend), built from source:

```bash
docker compose -f infra/docker-compose.yml --profile app up --build -d --wait
```

Open http://localhost:8088, create a supplier account and you land on the dashboard.

Stop it:

```bash
docker compose -f infra/docker-compose.yml --profile app down
```

## Development

For hot reload, run PostgreSQL in Docker and the apps on your machine. Requirements: JDK 25,
Node 22.22.3+ (see `frontend/.nvmrc`) and Docker.

```bash
docker compose -f infra/docker-compose.yml up -d --wait
```

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
cd frontend && npm ci && npm start
```

The app is at http://localhost:4200 (the dev server proxies `/api` to the backend on 8080).

All commands — full checks, formatting, the API client, end-to-end tests — are in the
**Commands** section of [CLAUDE.md](CLAUDE.md).

## How it is built

| Part     | Technology                                                                                                           |
| -------- | -------------------------------------------------------------------------------------------------------------------- |
| Backend  | Java 25, Spring Boot 4, Spring Modulith (modular monolith), PostgreSQL, Flyway                                       |
| Frontend | Angular 22 (standalone components, signals), Angular Material                                                        |
| API      | REST under `/api/v1`, contract in [`docs/api/openapi.json`](docs/api/openapi.json), Angular client generated from it |
| Security | Server sessions in PostgreSQL, HttpOnly cookies, CSRF protection, tenant isolation by `supplier_id`                  |
| Quality  | NullAway, Spotless, ESLint, Prettier, JUnit + Testcontainers, Vitest, Playwright                                     |
| CI       | GitHub Actions on every PR: backend, frontend, end-to-end, Docker images, dependency scan                            |

Read more:

- [Product](docs/PRODUCT.md): features, MCP tools and data model
- [Architecture](docs/ARCHITECTURE.md): modules, layers and patterns
- [Roadmap](docs/ROADMAP.md): milestones and their "Done when"
- [Decisions](docs/adr/): modular monolith, authentication, background jobs, UI library

## Contributing

Work happens in small pull requests into `main`. Every PR needs all CI checks to pass; `main`
is protected. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org).
