# 0001: Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-11-09

## Context
AgentShelf is built by a very small team on a tight budget. It needs clear boundaries
(catalog, rules, quotes, agent gateway and so on) so the code stays maintainable. It must also
scale out behind a load balancer. Running and debugging many services would cost more time
and money than we have.

## Decision
One deployable Spring Boot backend, split into modules under `com.agentshelf.<module>`.
Spring Modulith enforces the boundaries, and `ApplicationModules.verify()` runs in the test
suite (`ModularityTest`). Modules talk only through another module's public root package or
through domain events.

## Options considered
- Microservices: independent scaling and deploys, but network calls, distributed data,
  more infrastructure and much harder local development. Too heavy for this stage.
- Plain monolith (packages by layer): simplest to start, but nothing stops modules from
  reaching into each other, so boundaries erode over time.
- Modular monolith with Spring Modulith: one process to run and test, boundaries checked
  in every build, events stored in the database, and a module can be extracted later.

## Consequences
- Easier: local setup, transactions, debugging, refactoring across modules.
- Harder: every module scales together. Workers can run as a separate deployment of the
  same code if needed (see the scaling path in `ARCHITECTURE.md`).
- Watch: keep `shared` tiny, never share entities across modules, and keep the backend
  stateless so many instances can run.
- Build toolchain chosen with this slice: Java 25 (Temurin), Spring Boot 4.1,
  Spring Modulith 2.1, Error Prone + NullAway (JSpecify mode, scoped to `com.agentshelf`),
  Spotless with google-java-format 2.0.
