# ECL v3.1 V1 Frontend UI Alignment Design

> Date: 2026-06-21
> Scope: frontend only
> Source: `design-variants-cp/` V1 design variants

## Goal

Align the React frontend with the V1 Refined Enterprise / Clean Professional UI in `design-variants-cp/`, without expanding backend API work in this pass.

## Approved Approach

Use approach B: introduce shared V1 page primitives and refactor the current pages to use the design structure from the HTML variants.

This keeps current API calls and CRUD behavior where they exist, while making the screen layout, spacing, navigation, tables, tabs, chips, panels, module cards, and empty states consistent with the V1 design.

## In Scope

- Global app shell: 52px white top bar, optional 224px scheme sidebar, stable content area.
- Shared UI CSS: page container, table, action buttons, tabs, toolbar/action bar, info notes, module cards, breadcrumbs, split layouts.
- Scheme pages: list, overview, compare visual container alignment where practical.
- Parameter pages: risk groups, stage, PD, LGD, CCF, overlay.
- Replace decorative emoji/navigation glyphs in React with Ant Design icons or CSS status markers.
- Keep backend interfaces as-is; missing or immature API behaviors should degrade through empty states, selection prompts, or existing local UI state.

## Out of Scope

- Backend endpoint changes.
- Calculation engine, reports, trial center, and job monitor implementation.
- Full static mock replacement with hardcoded data.
- Large routing changes beyond preserving current V1 page mapping.

## Design Rules

- Use the existing token system in `src/styles/tokens.css`.
- Prefer shared CSS classes over repeated page-local `<style>` blocks.
- Use Ant Design controls for forms/modals/selects, but neutralize table/page visuals to match V1.
- Keep page width at 1280px with `24px 32px` page padding.
- Keep cards and panels at 12px radius only where the V1 design uses panel containers; avoid nested decorative cards.
- Preserve current API data flow and mutation behavior.

## Verification

- `npm run build` from `ecl-system/ecl-frontend`.
- Start Vite dev server and inspect the main V1 routes.
