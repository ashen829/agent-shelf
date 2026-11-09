# 0003: Background jobs — db-scheduler on PostgreSQL

- Status: Accepted
- Date: 2026-11-09

## Context
Several features do work that is too slow for an HTTP request: CSV and Excel imports (M1),
AI cleanup through the Claude Message Batches API (M2), and scheduled work such as expiring old
quotes (M5). CLAUDE.md requires these to run as background jobs, never inside a request.

The job runner must:
- work with several backend instances: each job runs exactly once, on one instance;
- survive crashes: a job whose instance dies is noticed and run again;
- retry with backoff, for example when the Claude API is briefly unavailable;
- run recurring tasks (every hour, every night);
- keep all state in PostgreSQL. No Redis or message broker yet (see the scaling path in
  `ARCHITECTURE.md`).

## Decision
Use [db-scheduler](https://github.com/kagkarlsson/db-scheduler) with its Spring Boot 4 starter
(`com.github.kagkarlsson:db-scheduler-spring-boot-4-starter`, 17.0.0 at the time of writing).
It stores executions in one PostgreSQL table, `scheduled_tasks`, created by a Flyway migration
when the first job is added (M1).

How we use it:
- **db-scheduler decides when and where a job runs. Our tables hold what users see.** Each
  module keeps its own job table (`catalog.import_job`, `enrichment.enrichment_job`) with status,
  row counts, error reports and token counts, scoped by `supplier_id`. The db-scheduler task only
  carries the job ID.
- **Polling strategy `lock-and-fetch`**: on PostgreSQL this is a single
  `SELECT ... FOR UPDATE SKIP LOCKED` statement, so instances never pick the same execution.
- **Idempotent tasks**: a task can run again after a crash, so it checks its job's status first
  and continues or no-ops.
- **Retries**: failures retry with exponential backoff up to a limit, then the job is marked
  `FAILED` in the module table and shown in the dashboard.
- **Recurring tasks** (quote expiry in M5) use db-scheduler's recurring schedules, which also run
  once across all instances.
- **Ports and adapters**: tasks are thin. They call the module's application service, so the
  business logic doesn't depend on db-scheduler.
- Job duration and outcome are recorded as Micrometer metrics.

## Options considered
- **db-scheduler**: small, Apache-2.0, actively maintained, PostgreSQL with single-statement
  lock-and-fetch, detects dead executions with heartbeats, supports one-off and recurring tasks,
  has a Spring Boot 4 starter. Adds one dependency.
- **Our own runner** (`SELECT ... FOR UPDATE SKIP LOCKED` on a jobs table, as first sketched in
  `ARCHITECTURE.md`): no dependency and full control, but we would write and maintain
  heartbeats, dead-execution recovery, retries, backoff and recurring schedules ourselves. That
  is subtle code for a small team, and bugs there lose or duplicate work.
- **JobRunr**: polished, with a built-in dashboard, but LGPL-3.0 and several features (such as
  some batch and dashboard capabilities) are in the paid Pro edition. Heavier than we need.
- **Quartz**: proven and clustered via JDBC, but much more configuration, several tables and an
  older API.
- **`@Async` or Spring Modulith event listeners alone**: fine for short side effects, but not
  for long jobs that must survive restarts and retry on a schedule.

## Consequences
- Easier: safe multi-instance execution, crash recovery and recurring tasks from day one, with
  only PostgreSQL to run.
- Harder: one more library to keep up to date (Dependabot covers it). Its table is outside our
  modules: it lives in its own `jobs` schema and holds no supplier data.
- Watch: polling adds a small, steady load on PostgreSQL. Keep the default poll interval unless
  metrics show a need, and keep task payloads to IDs only.
- If job volume outgrows PostgreSQL, step 4 of the scaling path (run workers as a separate
  deployment of the same code) still works unchanged with db-scheduler.
