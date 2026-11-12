# data/

| Folder | Contents | Committed? |
|---|---|---|
| `raw/` | Source CSV files exactly as downloaded (e.g. `matches.csv`, `deliveries.csv`) | No (git-ignored) |
| `reference/` | Hand-curated mapping files: team name history, venue aliases, player aliases | Yes |

Rules:
- Never edit files in `raw/`. Every correction belongs in `reference/` or in the ingestion transform, so it can be reviewed and repeated.
- Small, hand-verified fixture datasets used by tests live under `backend/src/test/resources/`, not here.
- The ingestion CLI (V1) reads from `raw/` and records each file's SHA-256 hash in `core.ingestion_run`.
