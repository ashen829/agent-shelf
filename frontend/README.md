# AgentShelf frontend

Angular supplier dashboard. Commands are in the root `CLAUDE.md`; architecture rules are in
`docs/ARCHITECTURE.md`; the UI library choice is in `docs/adr/0004-ui-library.md`.

## Layout

```text
src/app/
  core/       layout (shell), and later auth, HTTP interceptors and error handling
  shared/     reusable UI components and pipes
  features/   one folder per feature, lazy-loaded routes
  api/        generated OpenAPI client (never edit by hand)
```

## Notes on package.json

- `overrides.minimatch`: ESLint and typescript-eslint still depend on minimatch 10, which has
  a denial-of-service advisory (GHSA-g3hv-75wg-m249) fixed only in 11. It is a dev-only
  dependency, but the override keeps the vulnerability scan clean. Remove the override once
  ESLint depends on minimatch 11 or later.
- `allowScripts`: npm 12 blocks dependency install scripts unless allowed. The five listed
  packages are denied on purpose: the build and tests work without them, and fewer install
  scripts means less supply-chain risk. CI installs the same npm version (`packageManager`).
