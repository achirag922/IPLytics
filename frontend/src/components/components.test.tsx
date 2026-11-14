import { screen, within } from '@testing-library/react';
import { Trophy } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api/client';
import { StatCard } from '@/components/cards/StatCard';
import { BarChart, WormChart } from '@/components/charts';
import { FilterBar, SelectFilter } from '@/components/filters';
import { ChartSkeleton, PageSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { PaginationControls } from '@/components/tables/PaginationControls';
import { chartOption } from '@/test/FakeEChart';
import { renderWithProviders } from '@/test/renderApp';

describe('StatCard', () => {
  it('shows a label, a formatted value and a hint', () => {
    renderWithProviders(<StatCard label="Matches" value="1,243" hint="2008–2026" icon={Trophy} />);
    expect(screen.getByText('Matches')).toBeInTheDocument();
    expect(screen.getByText('1,243')).toBeInTheDocument();
    expect(screen.getByText('2008–2026')).toBeInTheDocument();
  });
});

describe('FilterBar', () => {
  it('labels its selects, maps the "all" choice to undefined and resets', async () => {
    const onChange = vi.fn();
    const onReset = vi.fn();
    const { user } = renderWithProviders(
      <FilterBar onReset={onReset}>
        <SelectFilter
          label="Stage"
          value="Final"
          allLabel="All stages"
          options={[
            { value: 'Final', label: 'Final' },
            { value: 'League', label: 'League' },
          ]}
          onChange={onChange}
        />
      </FilterBar>,
    );
    const form = screen.getByRole('search', { name: 'Filters' });
    const select = within(form).getByLabelText('Stage');
    expect(select).toHaveValue('Final');
    await user.selectOptions(select, 'League');
    expect(onChange).toHaveBeenLastCalledWith('League');
    await user.selectOptions(select, 'All stages');
    expect(onChange).toHaveBeenLastCalledWith(undefined);
    await user.click(screen.getByRole('button', { name: 'Reset' }));
    expect(onReset).toHaveBeenCalledOnce();
  });

  it('disables reset when no filter is active', () => {
    renderWithProviders(
      <FilterBar onReset={() => undefined} resetDisabled>
        <span>filters</span>
      </FilterBar>,
    );
    expect(screen.getByRole('button', { name: 'Reset' })).toBeDisabled();
  });
});

interface Row {
  id: number;
  name: string;
  matches: number;
}

const columns: Column<Row>[] = [
  { key: 'name', header: 'Name', cell: (r) => r.name },
  { key: 'm', header: 'M', title: 'Matches', align: 'right', cell: (r) => r.matches },
];

describe('DataTable', () => {
  it('renders an accessible table with abbreviated headers', () => {
    renderWithProviders(
      <DataTable
        caption="Teams"
        columns={columns}
        rows={[{ id: 1, name: 'MI', matches: 280 }]}
        rowKey={(r) => r.id}
      />,
    );
    const table = screen.getByRole('table', { name: 'Teams' });
    expect(within(table).getByRole('columnheader', { name: 'M' }).querySelector('abbr')).toHaveAttribute(
      'title',
      'Matches',
    );
    expect(within(table).getByRole('cell', { name: '280' })).toBeInTheDocument();
  });

  it('shows the empty state instead of an empty table', () => {
    renderWithProviders(
      <DataTable
        caption="Teams"
        columns={columns}
        rows={[]}
        rowKey={(r) => r.id}
        emptyTitle="No teams found"
      />,
    );
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByText('No teams found')).toBeInTheDocument();
  });
});

describe('PaginationControls', () => {
  it('shows the range and moves between pages', async () => {
    const onPageChange = vi.fn();
    const { user } = renderWithProviders(
      <PaginationControls
        pagination={{ page: 0, size: 25, totalElements: 60, totalPages: 3 }}
        onPageChange={onPageChange}
      />,
    );
    expect(screen.getByText(/1–25 of 60/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Previous/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /Next/ }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});

describe('loading states', () => {
  it.each([
    ['stat cards', <StatCardSkeleton key="s" />, 'Loading statistics'],
    ['table', <TableSkeleton key="t" />, 'Loading table'],
    ['chart', <ChartSkeleton key="c" />, 'Loading chart'],
    ['page', <PageSkeleton key="p" />, 'Loading page'],
  ])('%s skeleton is announced as loading', (_, element, label) => {
    renderWithProviders(element);
    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-busy', 'true');
    expect(status).toHaveTextContent(label);
  });
});

describe('EmptyState', () => {
  it('shows the title and description', () => {
    renderWithProviders(<EmptyState title="No matchup found" description="They never met." />);
    expect(screen.getByText('No matchup found')).toBeInTheDocument();
    expect(screen.getByText('They never met.')).toBeInTheDocument();
  });
});

describe('ErrorState', () => {
  it.each([
    [new ApiError(404, 'Player 9 not found'), /could not find/],
    [new ApiError(400, "Invalid value 'x'"), /filters cannot be applied/],
    [new ApiError(500, 'NullPointerException at line 4'), /went wrong on the server/],
    [new ApiError(0, 'Failed to fetch'), /could not be reached/],
  ])('explains %s without exposing the API message', (error, message) => {
    renderWithProviders(<ErrorState error={error} />);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Unable to load data.');
    expect(alert).toHaveTextContent(message);
    expect(alert).not.toHaveTextContent(error.message);
  });

  it('retries', async () => {
    const onRetry = vi.fn();
    const { user } = renderWithProviders(<ErrorState error={new ApiError(500, 'x')} onRetry={onRetry} />);
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});

describe('charts', () => {
  it('pass their data to ECharts through props', () => {
    renderWithProviders(
      <BarChart
        categories={[2025, 2026]}
        series={[{ name: 'Runs', data: [100, 200] }]}
        ariaLabel="Runs by season"
      />,
    );
    const option = chartOption(screen.getByRole('img', { name: 'Runs by season' }));
    expect(option.series[0]?.data).toEqual([100, 200]);
  });

  it('show an empty state instead of a broken chart', () => {
    renderWithProviders(<BarChart categories={[]} series={[{ name: 'Runs', data: [] }]} ariaLabel="Runs" />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('No data available')).toBeInTheDocument();
  });

  it('worm chart starts each innings at 0 and marks wicket overs', () => {
    renderWithProviders(
      <WormChart
        ariaLabel="Worm"
        innings={[
          { name: 'MI', overs: [{ over: 1, runs: 8, wickets: 1, cumulativeRuns: 8, cumulativeWickets: 1 }] },
        ]}
      />,
    );
    const option = chartOption(screen.getByRole('img', { name: 'Worm' }));
    expect(option.series[0]?.data).toEqual([
      [0, 0],
      [1, 8],
    ]);
    expect(option.series[1]?.data).toEqual([[1, 8, 1]]);
  });
});
