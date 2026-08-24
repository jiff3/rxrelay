# Frontend

## Product surface

The React/TypeScript application is a routed product interface rather than a generic administration template:

- **Overview** reports only persisted PostgreSQL totals, recent meaningful transitions, recently normalized medication identities, the latest ingestion counters, and the demo user's unread count.
- **Medication explorer** provides server-side text/status/manufacturer filtering, supported deterministic sorts, bounded pages, and URL-backed filter state.
- **Medication detail** separates normalized identity fields from openFDA source evidence and renders the stored transition timeline.
- **Watchlists and notifications** mutate the configured demo user's authoritative backend resources and refresh affected queries after success.
- **System activity** lists sanitized processed-event metadata and renders only lifecycle steps backed by durable records.
- **Data/about and architecture** document the source, matching strategy, limitations, and actual service topology without pretending a static diagram is live telemetry.

There is no fake analytics layer and no embedded medication dataset. Empty databases render actionable empty states. Null source fields render as “Unavailable,” not inferred values.

## Server state and failures

TanStack Query owns remote state and mutation invalidation; form/filter state remains local or in the route query string. API errors preserve the HTTP status, backend error code, request ID, and validation violations when supplied. The UI has distinct guidance for a disconnected backend and HTTP 429 responses, and retains prior page data while a bounded filter/page query refreshes.

Redis is never accessed by the browser. A Redis outage is handled behind the API and does not alter the web contract.

## Accessibility and responsive behavior

Pages use landmark elements, real headings, labels, buttons, links, tables, lists, and `time` elements. A keyboard-visible skip link targets the focusable main region. Interactive controls have high-contrast focus rings, status is conveyed with text as well as color, tables scroll within the viewport on narrow displays, and the primary navigation collapses to a native disclosure menu. Reduced-motion preferences disable decorative animation.

## Tests

Vitest and React Testing Library cover search, source-backed detail/timeline data, watchlist creation, medication addition, event-generated notification display, pagination/filter requests, and the backend-offline state. All API responses in these tests are controlled test fixtures; no test depends on FDA availability.

Playwright drives the critical routed journey in Chromium: search → detail/provenance → create watchlist → add medication → notification. Its API route is controlled so it remains deterministic. Manual verification against the real local pipeline is separate and documented in [development.md](development.md).

```bash
cd apps/web
npm run lint
npm test
npm run build
npm run test:e2e
```

## Screenshots

Screenshots must come from a running backend with persisted source data. `npm run capture:screenshots` checks backend health and refuses to run when the overview reports zero shortage records. With the gateway/core on port 8080 and Vite on 5173:

```bash
cd apps/web
npm run capture:screenshots
```

The script captures the overview, medication explorer, and system activity at a fixed 1440-pixel viewport under `docs/screenshots/`. Review the images before committing them; never substitute mocked or hand-edited product data.
