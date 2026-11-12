# 0006 Testing strategy with Testcontainers

**Status:** Accepted (V0)

## Context
Most of the logic lives in PostgreSQL-specific SQL (materialized views, `COPY`, `ON CONFLICT`). An in-memory database such as H2 cannot run it faithfully.

## Decision

| Layer | Tooling | Naming / phase |
|---|---|---|
| Unit (services, metric formulas) | JUnit 5, Mockito, AssertJ | `*Test`, run by `mvn test` (surefire) |
| Web slice (controllers) | `@WebMvcTest` with `@MockitoBean` | `*Test` |
| Integration (repositories, SQL, migrations, ingestion, full HTTP) | `@SpringBootTest` with **Testcontainers** `postgres:16-alpine` via `@ServiceConnection` | `*IT`, run by `mvn verify` (failsafe) |
| Frontend | Vitest, React Testing Library, jsdom | `*.test.ts(x)` |

- Integration tests extend `AbstractIntegrationTest`, so they share one Spring context and one container.
- From V1, ingestion is verified against a small hand-checked fixture (2–3 real matches) whose scorecards are asserted exactly.
- CI runs both `mvn verify` and the frontend lint, typecheck, test and build on every push and pull request.

## Consequences
- `mvn verify` requires Docker. This is available locally (Docker Desktop) and on GitHub-hosted runners.
- Tests use the same database engine and major version as production.
