package com.iplytics.transform;

import static java.util.stream.Collectors.joining;

import java.util.List;

/**
 * SQL of the staging -> core transformation. Everything is set-based: staging rows are first copied into typed
 * temporary tables (dropped at commit), then each core table is upserted on its natural key and stale rows are
 * pruned. Run ids are numbers read from core.ingestion_run, never user input.
 */
final class TransformSql {

    private TransformSql() {
    }

    static final String LOCK = "select pg_advisory_xact_lock(7101201)";

    static final String LATEST_RUNS = """
            select dataset, max(id) as run_id from core.ingestion_run
             where status = 'COMPLETED' and dataset in ('MATCHES', 'DELIVERIES', 'MATCH_PLAYERS')
             group by dataset""";

    // ---- typed copies of the staging batches ---------------------------------------------------------------

    static String stageMatches(long runId) {
        return """
                create temp table t_match on commit drop as
                select m.id::bigint                                    as source_match_id,
                       m.season                                        as source_season,
                       extract(year from m."date"::date)::smallint     as season_year,
                       m.season_year                                   as staged_season_year,
                       m."date"::date                                  as match_date,
                       coalesce(m.end_date, m."date")::date            as end_date,
                       m.match_number::smallint                        as match_number,
                       coalesce(m.match_type, 'League')                as stage,
                       m.venue,
                       m.city,
                       m.source_unmapped::jsonb #>> array['info', 'registry', 'venues', m.venue] as cricsheet_venue_id,
                       m.team1, m.team2, m.toss_winner, m.toss_decision, m.winner, m.super_over_winner,
                       m.result,
                       m.result_margin::numeric::smallint              as result_margin,
                       m.method,
                       coalesce(upper(m.super_over) = 'Y', false)      as has_super_over,
                       m.player_of_match, m.player_of_match_ids,
                       m.target_runs::numeric::smallint                as target_runs,
                       m.target_overs::numeric(4, 1)                   as target_overs,
                       m.scheduled_overs::numeric::smallint            as scheduled_overs,
                       nullif(array_remove(array[m.umpire1, m.umpire2], null), '{}') as umpires,
                       string_to_array(m.tv_umpires, '|')              as tv_umpires,
                       string_to_array(m.match_referees, '|')          as match_referees,
                       string_to_array(m.reserve_umpires, '|')         as reserve_umpires,
                       m.source_revision::integer                      as source_revision,
                       m.source_data_version,
                       m.source_created::date                          as source_created,
                       m.ingestion_run_id
                  from staging.matches m
                 where m.ingestion_run_id = %d""".formatted(runId);
    }

    static String stageDeliveries(long runId) {
        return """
                create temp table t_delivery on commit drop as
                select d.match_id::bigint                         as source_match_id,
                       d.inning::smallint                         as innings_number,
                       coalesce(d.is_super_over = '1', false)     as is_super_over,
                       d.batting_team, d.bowling_team,
                       d."over"::smallint                         as over_number,
                       d.ball::smallint                           as ball_in_over,
                       d.actual_delivery,
                       d.batter,      nullif(d.batter_id, '')      as batter_id,
                       d.bowler,      nullif(d.bowler_id, '')      as bowler_id,
                       d.non_striker, nullif(d.non_striker_id, '') as non_striker_id,
                       d.batsman_runs::smallint                   as batter_runs,
                       d.extra_runs::smallint                     as extra_runs,
                       d.total_runs::smallint                     as total_runs,
                       coalesce(d.wides, '0')::smallint           as wides,
                       coalesce(d.noballs, '0')::smallint         as noballs,
                       coalesce(d.byes, '0')::smallint            as byes,
                       coalesce(d.legbyes, '0')::smallint         as legbyes,
                       coalesce(d.penalty, '0')::smallint         as penalty,
                       coalesce(d.non_boundary = '1', false)      as non_boundary,
                       (d.is_wicket = '1')                        as is_wicket,
                       d.player_dismissed,       nullif(d.player_dismissed_id, '')       as player_dismissed_id,
                       d.other_player_dismissed, nullif(d.other_player_dismissed_id, '') as other_player_dismissed_id,
                       d.fielder, d.fielder_ids, d.other_fielder, d.other_fielder_ids,
                       d.wickets_json::jsonb                      as wickets_json
                  from staging.deliveries d
                 where d.ingestion_run_id = %d""".formatted(runId);
    }

    static String stageMatchPlayers(long runId) {
        return """
                create temp table t_match_player on commit drop as
                select p.match_id::bigint          as source_match_id,
                       p.team,
                       p.player,
                       nullif(p.player_id, '')     as cricsheet_id,
                       p.list_position::smallint   as list_position
                  from staging.match_players p
                 where p.ingestion_run_id = %d""".formatted(runId);
    }

    static final List<String> STAGE_DERIVED = List.of(
            "create index on t_match (source_match_id)",
            "create index on t_delivery (source_match_id, innings_number, over_number, ball_in_over)",
            "create index on t_match_player (source_match_id, player)",
            """
            create temp table t_innings_src on commit drop as
            select source_match_id, innings_number,
                   min(batting_team) as batting_team, count(distinct batting_team) as batting_team_count,
                   min(bowling_team) as bowling_team, count(distinct bowling_team) as bowling_team_count,
                   bool_or(is_super_over) as is_super_over,
                   bool_or(is_super_over) = bool_and(is_super_over) as consistent_super_over
              from t_delivery group by 1, 2""",
            """
            create temp table t_wicket_src on commit drop as
            select d.source_match_id, d.innings_number, d.over_number, d.ball_in_over,
                   w.ord::smallint as wicket_number,
                   w.value ->> 'player_out' as player_out,
                   w.value ->> 'kind' as kind,
                   w.value -> 'fielders' as fielders
              from t_delivery d
             cross join lateral jsonb_array_elements(d.wickets_json) with ordinality as w(value, ord)
             where d.wickets_json is not null""",
            """
            create temp table t_fielder_src on commit drop as
            select w.source_match_id, w.innings_number, w.over_number, w.ball_in_over, w.wicket_number,
                   f.ord::smallint as fielder_number,
                   f.value ->> 'name' as name,
                   coalesce((f.value ->> 'substitute')::boolean, false) as is_substitute
              from t_wicket_src w
             cross join lateral jsonb_array_elements(coalesce(w.fielders, '[]'::jsonb)) with ordinality as f(value, ord)""",
            // every (match, name, registry id) pair the source mentions, from any column
            """
            create temp table t_person_raw on commit drop as
            select source_match_id, name, nullif(cricsheet_id, '') as cricsheet_id from (
                select source_match_id, player as name, cricsheet_id from t_match_player
                union all select source_match_id, batter, batter_id from t_delivery
                union all select source_match_id, bowler, bowler_id from t_delivery
                union all select source_match_id, non_striker, non_striker_id from t_delivery
                union all select source_match_id, player_dismissed, player_dismissed_id from t_delivery
                union all select source_match_id, other_player_dismissed, other_player_dismissed_id from t_delivery
                union all select d.source_match_id, f.name, f.id from t_delivery d,
                    unnest(string_to_array(d.fielder, '|'), string_to_array(d.fielder_ids, '|')) as f(name, id)
                    where d.fielder is not null
                union all select d.source_match_id, f.name, f.id from t_delivery d,
                    unnest(string_to_array(d.other_fielder, '|'), string_to_array(d.other_fielder_ids, '|')) as f(name, id)
                    where d.other_fielder is not null
                union all select m.source_match_id, p.name, p.id from t_match m,
                    unnest(string_to_array(m.player_of_match, '|'), string_to_array(m.player_of_match_ids, '|')) as p(name, id)
                    where m.player_of_match is not null
                union all select source_match_id, player_out, null from t_wicket_src
                union all select source_match_id, name, null from t_fielder_src
            ) x where name is not null""",
            """
            create temp table t_person on commit drop as
            select source_match_id, name, min(cricsheet_id) as cricsheet_id, count(distinct cricsheet_id) as id_count
              from t_person_raw group by 1, 2""",
            """
            create temp table t_team_usage on commit drop as
            select distinct u.name, u.season_year from (
                select team1 as name, season_year from t_match
                union all select team2, season_year from t_match
                union all select toss_winner, season_year from t_match
                union all select winner, season_year from t_match
                union all select super_over_winner, season_year from t_match
                union all select i.batting_team, m.season_year from t_innings_src i join t_match m using (source_match_id)
                union all select i.bowling_team, m.season_year from t_innings_src i join t_match m using (source_match_id)
                union all select p.team, m.season_year
                    from (select distinct source_match_id, team from t_match_player) p join t_match m using (source_match_id)
            ) u where u.name is not null""",
            """
            create temp table t_venue_usage on commit drop as
            select venue as alias,
                   mode() within group (order by city) as source_city,
                   min(cricsheet_venue_id) as cricsheet_venue_id
              from t_match group by venue""",
            "analyze t_match",
            "analyze t_delivery",
            "analyze t_match_player",
            "analyze t_person");

    // ---- reference data (loaded by Java into these tables) ---------------------------------------------------

    static final List<String> CREATE_REFERENCE = List.of(
            """
            create temp table t_ref_team (code text primary key, name text not null, is_active boolean not null,
                home_city text) on commit drop""",
            """
            create temp table t_ref_team_name (name text primary key, code text not null,
                first_season smallint not null, last_season smallint) on commit drop""",
            "create temp table t_ref_venue (alias text primary key, venue text not null, city text) on commit drop");

    /** Curated aliases of every curated venue in use, plus source names missing from the reference (reported). */
    static final String RESOLVE_VENUES = """
            create temp table t_venue_resolved on commit drop as
            with used_curated as (
                select distinct r.venue, r.city from t_ref_venue r join t_venue_usage u on u.alias = r.alias)
            select r.alias, r.venue as venue_name, r.city as venue_city, true as is_curated, u.cricsheet_venue_id
              from t_ref_venue r
              join used_curated c on c.venue = r.venue and c.city is not distinct from r.city
              left join t_venue_usage u on u.alias = r.alias
            union all
            select u.alias, u.alias, u.source_city, false, u.cricsheet_venue_id
              from t_venue_usage u
             where not exists (select 1 from t_ref_venue r where r.alias = u.alias)""";

    static final String PLAYER_MAP = """
            create temp table t_player_map on commit drop as
            select p.source_match_id, p.name, cp.id as player_id
              from t_person p join core.player cp on cp.cricsheet_id = p.cricsheet_id
            union all
            select p.source_match_id, p.name, cp.id
              from t_person p join core.player cp on cp.cricsheet_id is null and cp.display_name = p.name
             where p.cricsheet_id is null""";

    static final List<String> PLAYER_MAP_INDEX = List.of(
            "create unique index on t_player_map (source_match_id, name)",
            "create index on t_player_map (player_id)",
            "analyze t_player_map");

    static final String ALIASES = """
            create temp table t_alias on commit drop as
            select pm.player_id, pm.name, min(m.season_year) as first_season, max(m.season_year) as last_season
              from t_player_map pm join t_match m using (source_match_id)
             group by 1, 2""";

    static final List<String> DELIVERY_KEYS = List.of(
            """
            create temp table t_delivery_key on commit drop as
            select cm.source_match_id, ci.innings_number, cd.over_number, cd.ball_in_over, cd.id as delivery_id
              from core.delivery cd
              join core.innings ci on ci.id = cd.innings_id
              join core.match cm on cm.id = ci.match_id""",
            "create index on t_delivery_key (source_match_id, innings_number, over_number, ball_in_over)",
            "analyze t_delivery_key");

    // ---- upserts --------------------------------------------------------------------------------------------

    static final String UPSERT_SEASONS = upsert("core.season", List.of("year", "source_season"),
            "select distinct season_year, source_season from t_match",
            "(year)", List.of("source_season"));

    static final String UPSERT_TEAMS = upsert("core.team", List.of("code", "name", "is_active", "home_city"),
            """
            select distinct t.code, t.name, t.is_active, t.home_city
              from t_ref_team t
              join t_ref_team_name n on n.code = t.code
              join t_team_usage u on u.name = n.name""",
            "(code)", List.of("name", "is_active", "home_city"));

    static final String UPSERT_TEAM_NAMES = upsert("core.team_name_history",
            List.of("team_id", "name", "first_season", "last_season"),
            "select ct.id, n.name, n.first_season, n.last_season from t_ref_team_name n join core.team ct on ct.code = n.code",
            "(name)", List.of("team_id", "first_season", "last_season"));

    static final String UPSERT_VENUES = upsert("core.venue", List.of("name", "city", "is_curated"),
            "select venue_name, venue_city, bool_or(is_curated) from t_venue_resolved group by 1, 2",
            "on constraint uq_venue_name_city", List.of("is_curated"));

    static final String UPSERT_VENUE_ALIASES = upsert("core.venue_alias",
            List.of("venue_id", "alias", "is_curated", "cricsheet_venue_id"),
            """
            select v.id, r.alias, r.is_curated, r.cricsheet_venue_id
              from t_venue_resolved r
              join core.venue v on v.name = r.venue_name and v.city is not distinct from r.venue_city""",
            "(alias)", List.of("venue_id", "is_curated", "cricsheet_venue_id"));

    /** Players with a Cricsheet id; display name = name used in their most recent match. */
    static final String UPSERT_PLAYERS = upsert("core.player", List.of("cricsheet_id", "display_name"),
            """
            select distinct on (p.cricsheet_id) p.cricsheet_id, p.name
              from t_person p join t_match m using (source_match_id)
             where p.cricsheet_id is not null
             order by p.cricsheet_id, m.match_date desc, m.source_match_id desc, p.name""",
            "(cricsheet_id)", List.of("display_name"));

    /** Names the source gives no id for: identified by name, never merged with an id-bearing player. */
    static final String UPSERT_PLAYERS_WITHOUT_ID = upsert("core.player", List.of("cricsheet_id", "display_name"),
            "select distinct cast(null as text), p.name from t_person p where p.cricsheet_id is null",
            "(display_name) where cricsheet_id is null", List.of());

    static final String UPSERT_PLAYER_ALIASES = upsert("core.player_alias",
            List.of("player_id", "name", "first_season", "last_season"),
            "select player_id, name, first_season, last_season from t_alias",
            "(player_id, name)", List.of("first_season", "last_season"));

    static final List<String> MATCH_COLUMNS = List.of("source_match_id", "season_id", "match_date", "end_date",
            "match_number", "stage", "venue_id", "venue_name", "city", "team1_id", "team1_name", "team2_id",
            "team2_name", "toss_winner_team_id", "toss_decision", "result", "result_margin", "winner_team_id",
            "super_over_winner_team_id", "method", "has_super_over", "player_of_match_id", "target_runs",
            "target_overs", "scheduled_overs", "umpires", "tv_umpires", "match_referees", "reserve_umpires",
            "source_revision", "source_data_version", "source_created", "source_ingestion_run_id");

    static final String UPSERT_MATCHES = upsert("core.match", MATCH_COLUMNS,
            """
            select m.source_match_id, s.id, m.match_date, m.end_date, m.match_number, m.stage, va.venue_id,
                   m.venue, m.city, t1.team_id, m.team1, t2.team_id, m.team2, tw.team_id, m.toss_decision,
                   m.result, m.result_margin, w.team_id, sw.team_id, m.method, m.has_super_over, pom.player_id,
                   m.target_runs, m.target_overs, m.scheduled_overs, m.umpires, m.tv_umpires, m.match_referees,
                   m.reserve_umpires, m.source_revision, m.source_data_version, m.source_created, m.ingestion_run_id
              from t_match m
              join core.season s on s.year = m.season_year
              join core.venue_alias va on va.alias = m.venue
              join core.team_name_history t1 on t1.name = m.team1
              join core.team_name_history t2 on t2.name = m.team2
              left join core.team_name_history tw on tw.name = m.toss_winner
              left join core.team_name_history w on w.name = m.winner
              left join core.team_name_history sw on sw.name = m.super_over_winner
              left join t_player_map pom
                     on pom.source_match_id = m.source_match_id and pom.name = split_part(m.player_of_match, '|', 1)""",
            "(source_match_id)", MATCH_COLUMNS.subList(1, MATCH_COLUMNS.size()));

    static final String UPSERT_INNINGS = upsert("core.innings",
            List.of("match_id", "innings_number", "batting_team_id", "bowling_team_id", "is_super_over"),
            """
            select cm.id, i.innings_number, bt.team_id, bw.team_id, i.is_super_over
              from t_innings_src i
              join core.match cm on cm.source_match_id = i.source_match_id
              join core.team_name_history bt on bt.name = i.batting_team
              join core.team_name_history bw on bw.name = i.bowling_team""",
            "(match_id, innings_number)", List.of("batting_team_id", "bowling_team_id", "is_super_over"));

    static final List<String> DELIVERY_COLUMNS = List.of("innings_id", "over_number", "ball_in_over",
            "actual_delivery", "is_super_over", "batter_id", "non_striker_id", "bowler_id", "batter_runs",
            "extra_runs", "total_runs", "wides", "noballs", "byes", "legbyes", "penalty", "non_boundary", "is_wicket");

    static final String UPSERT_DELIVERIES = upsert("core.delivery", DELIVERY_COLUMNS,
            """
            select ci.id, d.over_number, d.ball_in_over, d.actual_delivery, d.is_super_over,
                   b.player_id, ns.player_id, bw.player_id, d.batter_runs, d.extra_runs, d.total_runs,
                   d.wides, d.noballs, d.byes, d.legbyes, d.penalty, d.non_boundary, d.is_wicket
              from t_delivery d
              join core.match cm on cm.source_match_id = d.source_match_id
              join core.innings ci on ci.match_id = cm.id and ci.innings_number = d.innings_number
              join t_player_map b  on b.source_match_id = d.source_match_id and b.name = d.batter
              join t_player_map ns on ns.source_match_id = d.source_match_id and ns.name = d.non_striker
              join t_player_map bw on bw.source_match_id = d.source_match_id and bw.name = d.bowler""",
            "(innings_id, over_number, ball_in_over)", DELIVERY_COLUMNS.subList(3, DELIVERY_COLUMNS.size()));

    static final String UPSERT_WICKETS = upsert("core.wicket",
            List.of("delivery_id", "wicket_number", "player_out_id", "kind"),
            """
            select k.delivery_id, w.wicket_number, p.player_id, w.kind
              from t_wicket_src w
              join t_delivery_key k using (source_match_id, innings_number, over_number, ball_in_over)
              join t_player_map p on p.source_match_id = w.source_match_id and p.name = w.player_out""",
            "(delivery_id, wicket_number)", List.of("player_out_id", "kind"));

    static final String UPSERT_WICKET_FIELDERS = upsert("core.wicket_fielder",
            List.of("wicket_id", "fielder_number", "player_id", "is_substitute"),
            """
            select cw.id, f.fielder_number, p.player_id, f.is_substitute
              from t_fielder_src f
              join t_delivery_key k using (source_match_id, innings_number, over_number, ball_in_over)
              join core.wicket cw on cw.delivery_id = k.delivery_id and cw.wicket_number = f.wicket_number
              join t_player_map p on p.source_match_id = f.source_match_id and p.name = f.name""",
            "(wicket_id, fielder_number)", List.of("player_id", "is_substitute"));

    static final String UPSERT_MATCH_PLAYERS = upsert("core.match_player",
            List.of("match_id", "player_id", "team_id", "list_position"),
            """
            select cm.id, p.player_id, tn.team_id, mp.list_position
              from t_match_player mp
              join core.match cm on cm.source_match_id = mp.source_match_id
              join core.team_name_history tn on tn.name = mp.team
              join t_player_map p on p.source_match_id = mp.source_match_id and p.name = mp.player""",
            "(match_id, player_id)", List.of("team_id", "list_position"));

    static final List<String> ANALYZE_CORE = List.of(
            "analyze core.match", "analyze core.innings", "analyze core.delivery", "analyze core.wicket");

    // ---- pruning: core mirrors the latest staging batches -------------------------------------------------

    static final String DELETE_STALE_MATCHES = """
            delete from core.match cm
             where not exists (select 1 from t_match m where m.source_match_id = cm.source_match_id)""";

    static final String DELETE_STALE_INNINGS = """
            delete from core.innings ci using core.match cm
             where ci.match_id = cm.id
               and not exists (select 1 from t_innings_src s
                                where s.source_match_id = cm.source_match_id and s.innings_number = ci.innings_number)""";

    static final String DELETE_STALE_DELIVERIES = """
            delete from core.delivery cd using core.innings ci, core.match cm
             where cd.innings_id = ci.id and ci.match_id = cm.id
               and not exists (select 1 from t_delivery s
                                where s.source_match_id = cm.source_match_id and s.innings_number = ci.innings_number
                                  and s.over_number = cd.over_number and s.ball_in_over = cd.ball_in_over)""";

    static final String DELETE_STALE_WICKETS = """
            delete from core.wicket cw using t_delivery_key k
             where cw.delivery_id = k.delivery_id
               and not exists (select 1 from t_wicket_src s
                                where s.source_match_id = k.source_match_id and s.innings_number = k.innings_number
                                  and s.over_number = k.over_number and s.ball_in_over = k.ball_in_over
                                  and s.wicket_number = cw.wicket_number)""";

    static final String DELETE_STALE_WICKET_FIELDERS = """
            delete from core.wicket_fielder f using core.wicket cw, t_delivery_key k
             where f.wicket_id = cw.id and cw.delivery_id = k.delivery_id
               and not exists (select 1 from t_fielder_src s
                                where s.source_match_id = k.source_match_id and s.innings_number = k.innings_number
                                  and s.over_number = k.over_number and s.ball_in_over = k.ball_in_over
                                  and s.wicket_number = cw.wicket_number and s.fielder_number = f.fielder_number)""";

    static final String DELETE_STALE_MATCH_PLAYERS = """
            delete from core.match_player cmp using core.match cm
             where cmp.match_id = cm.id
               and not exists (select 1 from t_match_player mp
                                 join t_player_map p on p.source_match_id = mp.source_match_id and p.name = mp.player
                                where mp.source_match_id = cm.source_match_id and p.player_id = cmp.player_id)""";

    static final String DELETE_STALE_PLAYER_ALIASES = """
            delete from core.player_alias pa
             where not exists (select 1 from t_alias a where a.player_id = pa.player_id and a.name = pa.name)""";

    static final String DELETE_UNUSED_PLAYERS = """
            delete from core.player cp where not exists (select 1 from t_player_map m where m.player_id = cp.id)""";

    static final String DELETE_UNUSED_TEAM_NAMES = """
            delete from core.team_name_history n
             where not exists (select 1 from t_ref_team_name r where r.name = n.name)
                or not exists (select 1 from core.match m where n.team_id in (m.team1_id, m.team2_id))""";

    static final String DELETE_UNUSED_TEAMS = """
            delete from core.team t where not exists (select 1 from core.match m where t.id in (m.team1_id, m.team2_id))""";

    static final String DELETE_UNUSED_VENUE_ALIASES = """
            delete from core.venue_alias va
             where not exists (select 1 from t_venue_resolved r where r.alias = va.alias)
                or not exists (select 1 from core.match m where m.venue_id = va.venue_id)""";

    static final String DELETE_UNUSED_VENUES = """
            delete from core.venue v where not exists (select 1 from core.match m where m.venue_id = v.id)""";

    static final String DELETE_UNUSED_SEASONS = """
            delete from core.season s where not exists (select 1 from core.match m where m.season_id = s.id)""";

    static final List<String> CORE_TABLES = List.of("season", "team", "team_name_history", "venue", "venue_alias",
            "player", "player_alias", "match", "innings", "delivery", "wicket", "wicket_fielder", "match_player");

    /**
     * {@code INSERT ... SELECT ... ON CONFLICT ... DO UPDATE ... WHERE <changed>}, returning how many rows were
     * inserted and updated. Unchanged rows are not touched, so a rerun reports zero changes.
     */
    static String upsert(String table, List<String> columns, String select, String conflictTarget,
                         List<String> updateColumns) {
        String action;
        if (updateColumns.isEmpty()) {
            action = "do nothing";
        } else {
            String excluded = updateColumns.stream().map(c -> "excluded." + c).collect(joining(", "));
            String current = updateColumns.stream().map(c -> "t." + c).collect(joining(", "));
            action = "do update set (" + String.join(", ", updateColumns) + ") = row(" + excluded + ")"
                    + " where (" + current + ") is distinct from (" + excluded + ")";
        }
        return "with changed as (insert into " + table + " as t (" + String.join(", ", columns) + ") " + select
                + " on conflict " + conflictTarget + " " + action + " returning (xmax = 0) as inserted) "
                + "select count(*) filter (where inserted) as inserted, count(*) filter (where not inserted) as updated "
                + "from changed";
    }

    static String counted(String delete) {
        return "with gone as (" + delete + " returning 1) select count(*) from gone";
    }
}
