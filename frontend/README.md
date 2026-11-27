# WalletPay frontend

React and Vite provide the browser application. The backend is the authority for balances, ownership, validation and transaction state. This interface only handles financial sandbox balances.

## Run locally

Use Node.js 22.12+ (or a supported newer release). Start the backend on `127.0.0.1:8080`, then run from this directory:

```cmd
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`. Vite forwards `/api` to the backend. In production, the reverse proxy provides the same API path; no browser-side secret or external API URL is required.

## Verify

```cmd
npm run format:check
npm run test:run
npm run build
npm audit
```

Tests cover exact paise parsing, invalid amounts, password byte limits, CSRF headers, session errors, confirmation before transfer, one-time demo funding, retry idempotency, disabled bank fields, and navigation. Full browser/API checks live in the repository's `tests/e2e` directory.

## Organization

- `src/api`: credentialed requests, CSRF handling, ProblemDetail errors and CSV downloads.
- `src/auth`: in-memory user state backed by the server session.
- `src/components`: shared form controls, navigation and transaction displays.
- `src/hooks`: abortable data fetching.
- `src/lib`: integer money handling and password validation.
- `src/pages`: dashboard, transfers, initial demo funding, history, receipts, insights and profile.
- `src/theme`: light/dark appearance and live system preference detection.

The appearance selector is available in the workspace and on sign-in/registration pages. It applies across all routes and remembers the choice in this browser using the non-sensitive `walletpay.appearance` preference. The bottom profile row opens profile and sign-out actions; the WalletPay logo returns home, including from mobile navigation.

JWTs are never stored in `localStorage`, `sessionStorage` or JavaScript state. The server's HttpOnly cookie accompanies API requests. Before each mutation, the client obtains a CSRF token. Failed transfer/demo-credit retries retain their idempotency key. Logout only reports success after the server responds.

When demo funding is enabled on the server, each account can manually claim one free ₹100 initial demo credit. Further demo top-ups are not available. Bank account linking is visibly under development, with disabled fields that do not collect financial details. All balances and transfers are simulated and cannot be withdrawn.

The receipt print action uses the browser print dialog, including its Save as PDF option. CSV export comes from the authorized backend endpoint. No real bank/card details or webhook secrets belong in the browser.

The responsive layout includes keyboard focus states, labelled inputs, a skip link, accessible notices, reduced-motion support and mobile navigation. All images, styles and icons are local; no CDN is needed.
