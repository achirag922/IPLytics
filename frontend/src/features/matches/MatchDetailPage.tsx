import { Outlet } from 'react-router';
import type { MatchContext } from '@/features/detailContext';
import { ApiError } from '@/api/client';
import { PlayerLink, TeamLink, VenueLink } from '@/components/cards/EntityLinks';
import { PageHeader } from '@/components/layout/PageHeader';
import { TabNav } from '@/components/layout/TabNav';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { useMatchQuery } from '@/hooks/useMatchQueries';
import { useNumericParam } from '@/hooks/useNumericParam';
import { formatDate } from '@/lib/format';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const TABS = [
  { label: 'Scorecard', to: '' },
  { label: 'Progression', to: 'progression' },
];

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-sm">{children}</dd>
    </div>
  );
}

export function MatchDetailPage() {
  const matchId = useNumericParam('matchId');
  const match = useMatchQuery(matchId);

  if (matchId === undefined) return <NotFoundPage resource="Match" />;
  if (match.isPending) return <PageSkeleton label="Loading match" />;
  if (match.isError) {
    return match.error instanceof ApiError && match.error.kind === 'not-found' ? (
      <NotFoundPage resource="Match" />
    ) : (
      <ErrorState error={match.error} onRetry={() => void match.refetch()} />
    );
  }

  const m = match.data;
  const t1 = m.team1?.nameAtTheTime ?? m.team1?.team?.name;
  const t2 = m.team2?.nameAtTheTime ?? m.team2?.team?.name;
  return (
    <>
      <PageHeader
        breadcrumbs={[
          { label: 'Matches', to: '/matches' },
          { label: String(m.season), to: `/seasons/${m.season}` },
          { label: `${m.team1?.team?.code} v ${m.team2?.team?.code}` },
        ]}
        title={`${t1} v ${t2}`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-foreground">{m.resultText}</span>
            {m.stage && <Badge variant="outline">{m.stage}</Badge>}
            {m.method && <Badge variant="secondary">{m.method}</Badge>}
          </span>
        }
      />
      <Card className="mb-6">
        <CardContent className="pt-4 sm:pt-5">
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Fact label="Date">{formatDate(m.date)}</Fact>
            <Fact label="Season">
              {m.season}
              {m.matchNumber ? `, match ${m.matchNumber}` : ''}
            </Fact>
            <Fact label="Venue">
              <VenueLink venue={m.venue} />
            </Fact>
            <Fact label="Toss">
              {m.toss?.winner ? (
                <>
                  <TeamLink team={m.toss.winner} short /> chose to {m.toss.decision}
                </>
              ) : (
                '–'
              )}
            </Fact>
            <Fact label="Teams">
              <TeamLink team={m.team1?.team} /> v <TeamLink team={m.team2?.team} />
            </Fact>
            <Fact label="Result">{m.resultText}</Fact>
            <Fact label="Player of the match">
              <PlayerLink player={m.playerOfMatch} />
            </Fact>
            <Fact label="Umpires">{(m.officials?.umpires ?? []).join(', ') || '–'}</Fact>
          </dl>
        </CardContent>
      </Card>
      <TabNav label="Match sections" tabs={TABS} />
      <Outlet context={{ match: m } satisfies MatchContext} />
    </>
  );
}
