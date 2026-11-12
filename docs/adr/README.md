# Architecture Decision Records

Each ADR records one significant decision: its context, the decision itself, and the consequences.
ADRs are immutable once **Accepted**. To change one, add a new ADR that supersedes it.

| # | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith in a monorepo | Accepted |
| [0002](0002-postgres-flyway-layered-schemas.md) | PostgreSQL + Flyway with staging / core / analytics schemas | Accepted |
| [0003](0003-delivery-level-model.md) | Delivery-level fact model and franchise identity | Accepted |
| [0004](0004-cli-set-based-ingestion.md) | CLI-triggered, set-based ingestion | Accepted |
| [0005](0005-analytics-query-strategy.md) | Analytics query strategy | Accepted |
| [0006](0006-testing-strategy.md) | Testing strategy with Testcontainers | Accepted |
| [0007](0007-frontend-stack.md) | Frontend stack | Accepted |
| [0008](0008-api-conventions.md) | REST API conventions | Accepted |
| [0009](0009-initial-data-source.md) | Initial IPL data source | Proposed |

Template: copy any ADR and keep the headings **Status / Context / Decision / Consequences**.
