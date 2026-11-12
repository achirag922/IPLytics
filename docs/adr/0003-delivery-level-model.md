# 0003 Delivery-level fact model and franchise identity

**Status:** Accepted (V0, implemented in V1)

## Context
Advanced analytics (phase splits, batter-vs-bowler matchups, dismissal patterns, match progression) need ball-by-ball data. IPL franchises have been renamed (Delhi Daredevils → Delhi Capitals, Kings XI Punjab → Punjab Kings, Royal Challengers Bangalore → Bengaluru).

## Decision
- `core.delivery` (one row per ball) is the atomic fact table. Every statistic must be derivable from it plus `core.match`.
- Values derived once at ingestion and stored on `delivery`: `is_legal_ball` and `phase`.
- `core.team` represents the **franchise**. `core.team_name_history` maps every historical name to it with a season range. Defunct or distinct franchises (Deccan Chargers, Kochi Tuskers, Pune Warriors, Rising Pune Supergiant(s), Gujarat Lions) stay separate teams.
- Venue and player name variants resolve through alias tables that are curated in `data/reference/`.

## Consequences
- At about 1.1k matches and 260k deliveries, PostgreSQL aggregates delivery-level queries quickly with plain indexes.
- Head-to-head and season records are franchise-level by default. The name used in a given season is still available for display.
