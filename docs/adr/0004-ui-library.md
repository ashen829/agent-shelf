# 0004: UI component library — Angular Material

- Status: Accepted
- Date: 2026-11-09

## Context
The supplier dashboard needs forms, data tables with pagination and sorting, dialogs,
menus and a consistent theme. A small team must keep it working across yearly Angular
major upgrades, and it must be accessible.

## Decision
Use Angular Material with the Angular CDK, themed with Material 3 design tokens
(`mat.theme` and `--mat-sys-*` CSS variables).

## Options considered
- Angular Material + CDK: built by the Angular team and released in step with Angular,
  so upgrades come with `ng update` schematics. Strong accessibility, a solid data table,
  stable API, MIT licence. Fewer ready-made "rich" components; the default look is plain
  but themeable.
- PrimeNG: many ready-made components (advanced tables with filters, file upload, charts)
  that would speed up the import and review screens. History of breaking changes between
  majors, and long-term support for older majors is a paid product.

## Consequences
- Easier: upgrades, accessibility, and building our own pieces on the CDK (tables,
  overlays, drag and drop) when Material lacks something.
- Harder: richer widgets (for example a column-mapping grid or charts for insights) must
  be built on the CDK or added later; adding a chart library needs its own decision.
- Watch: style only through Material tokens and our own CSS variables, never by
  overriding Material's internal class names, so upgrades stay safe.
- Follow-up: fonts (Roboto, Material Symbols) load from Google Fonts for now. Self-host
  them before launch so we can lock down the Content Security Policy.
