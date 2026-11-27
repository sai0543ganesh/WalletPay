# Contributing

Start with the [local setup guide](docs/LOCAL_DEVELOPMENT.md) and keep each change focused on one improvement.

## Before opening a pull request

Run the checks relevant to your change from the directory shown:

| Changed area | Directory | Checks |
| --- | --- | --- |
| Backend Java | `backend/` | `mvn -B -ntp verify` |
| Frontend | `frontend/` | `npm run format:check`, `npm run test:run`, `npm run build` |
| Documentation | Repository root | `git diff --check` and verify relative links |

For changes to wallet balances, database queries, authentication or callbacks, also run the PostgreSQL integration profile described in [Testing](docs/TESTING.md). A disposable test database is required.

Describe what changed, why it helps and which checks you actually ran. Keep local credentials, generated reports and build output out of the commit. Report security concerns privately using the guidance in [Security](docs/SECURITY.md#reporting-a-problem).
