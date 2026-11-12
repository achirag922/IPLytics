# data/

| Folder | Contents | Committed? |
|---|---|---|
| `cricsheet/` | The official Cricsheet IPL JSON download (`ipl_json.zip`), exactly as downloaded | No (git-ignored) |
| `raw/` | Converter output: `matches.csv`, `deliveries.csv`, `match_players.csv`, `cricsheet-conversion-report.json` | No (git-ignored) |
| *(reference data)* | Curated franchise, team-name and venue-alias CSVs live **inside the app** at `backend/src/main/resources/reference/`, so they're versioned and tested with the code | Yes |

Rules:
- Never edit the Cricsheet zip or the generated CSVs by hand. Corrections belong in the reference CSVs or in code, so they can be reviewed and repeated.
- Test fixtures live under `backend/src/test/resources/`, not here.
- Each ingested file's SHA-256 hash is recorded in `core.ingestion_run`; the zip's hash is in the conversion report.

See [docs/data-source.md](../docs/data-source.md) for the source, licence and column mapping.
