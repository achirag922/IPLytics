# 0009 Initial IPL data source

**Status:** Proposed. To be confirmed at the start of V1.

## Context
V1 uses a CSV dataset. Two practical options:
1. **Kaggle-style IPL 2008–2024** (`matches.csv` + `deliveries.csv`). Simple and widely used, but players have short names only, with no stable ids and no full playing XI.
2. **Cricsheet** IPL CSV/JSON. It has stable player registry ids and full playing XIs. It is licensed ODC-BY, which requires attribution.

## Proposed decision
- Start with the Kaggle-style two-file format, because the priority is getting a small verified dataset through the full pipeline early.
- Keep `source_key` and alias tables in the schema so a later move to Cricsheet (V2) does not change `core`.
- First milestone: a 2–3 match fixture hand-verified against published scorecards, then one full season, then all seasons.

## Consequences
- The data limitations listed in `docs/metrics.md` §8 apply until the source changes.
