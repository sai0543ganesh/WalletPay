# Verification record

The application source imported into this repository matches the WalletPay version deployed on **27 November 2026** at [digitalwalletpay.duckdns.org](https://digitalwalletpay.duckdns.org/). Backend, frontend, infrastructure, development tools and browser tests were compared with the verified deployment source before publication. Documentation was adapted for this public repository. Live credentials, database records and generated build outputs are excluded.

## Executed checks on the deployed source

| Check | Verified result |
| --- | --- |
| Backend unit, service and security tests | 45 passed |
| PostgreSQL integration tests | 26 passed; zero failures, errors or skips |
| Frontend unit/component tests | 56 passed |
| Backend formatting and packaging | Passed |
| Frontend formatting and production build | Passed |
| Local real-browser journeys | 4 passed after the recipient phone-format fix |
| Public HTTPS real-browser journeys | 4 passed after deployment |
| Earlier real-HTTP smoke checks | 53 assertions passed during the demo-credit update |

The PostgreSQL tests include atomic transfers, insufficient balance, ordered locking, concurrent requests, retry idempotency, receipt ownership, one-time demo credit, disabled funding and migration preservation. The recipient regression tests verify a ₹69 transfer across Indian phone formats, reverse prefix lookup, missing-recipient errors, ambiguous aliases and self-transfer rejection without a debit.

The browser journeys cover registration, manual ₹100 demo credit, transfers between separate accounts, history/search/export/receipts, analytics, profile, login/logout, home-logo navigation and the avatar account menu. Theme checks cover light/dark/system selection, persistence across refresh, operating-system preference changes and mobile layout. Axe accessibility checks passed on the tested pages, including all six workspace routes in dark mode. Desktop and mobile screenshots were visually reviewed.

These results were recorded for the source before its import into this public repository. This repository's own verification workflow runs independently after publication; its current result is visible under [GitHub Actions](https://github.com/sai0543ganesh/WalletPay/actions).

## Deployment checks

Private database/source backups were created before application updates. The backend and frontend containers became healthy; the existing database and HTTPS-edge containers were retained. Secret-file checksums were preserved. The one-time-credit migration preserved existing wallet totals and pending deposits. Transfers were tested only with separate demo accounts, without submitting a transfer from the owner's account.

All money is simulated. Bank linking remains under development, with disabled fields. No genuine banking or payment-provider integration is implemented.

## Further validation

- Sustained load and resource sizing.
- Backup restoration and operational recovery drills.
- Multi-instance rate limiting and scaling.
- Independent security review and any future genuine payment integration.

Generated reports belong in ignored build/test directories or workflow artifacts. Do not commit credentials, user data, database backups or browser session material.
