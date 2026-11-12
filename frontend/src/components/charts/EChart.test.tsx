import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EChart } from './EChart';

// jsdom has no canvas, so the ECharts instance is replaced with a fake.
const fakeChart = vi.hoisted(() => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }));
vi.mock('./echarts', () => ({ echarts: { init: vi.fn(() => fakeChart) } }));

describe('EChart', () => {
  it('initialises, updates and disposes the chart instance', () => {
    const disconnect = vi.fn();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        disconnect = disconnect;
      },
    );
    const first = { series: [{ type: 'bar', data: [1, 2, 3] }] };
    const second = { series: [{ type: 'line', data: [3, 2, 1] }] };

    const { getByRole, rerender, unmount } = render(<EChart option={first} ariaLabel="Runs per season" />);

    expect(getByRole('img', { name: 'Runs per season' })).toBeInTheDocument();
    expect(fakeChart.setOption).toHaveBeenLastCalledWith(first, { notMerge: true });

    rerender(<EChart option={second} ariaLabel="Runs per season" />);
    expect(fakeChart.setOption).toHaveBeenLastCalledWith(second, { notMerge: true });

    unmount();
    expect(fakeChart.dispose).toHaveBeenCalled();
    expect(disconnect).toHaveBeenCalled();
  });
});
