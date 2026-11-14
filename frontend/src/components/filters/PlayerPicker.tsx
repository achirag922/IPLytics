import { Search } from 'lucide-react';
import { useId, useState, type KeyboardEvent } from 'react';
import type { PlayerListItem } from '@/api/playersApi';
import { Input, Label } from '@/components/ui/form';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { usePlayersQuery } from '@/hooks/usePlayerQueries';
import { cn } from '@/lib/utils';

interface PlayerPickerProps {
  label: string;
  onSelect: (player: PlayerListItem) => void;
  placeholder?: string;
  /** Player ids that cannot be picked again. */
  excludeIds?: number[];
  className?: string;
}

/**
 * Accessible combobox that searches players on the server (two or more characters, debounced), so the full
 * player list is never downloaded.
 */
export function PlayerPicker({
  label,
  onSelect,
  placeholder = 'Search by name…',
  excludeIds = [],
  className,
}: PlayerPickerProps) {
  const id = useId();
  const listId = `${id}-list`;
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const search = useDebouncedValue(term.trim(), 250);
  const query = usePlayersQuery(search.length >= 2 ? { search, size: 8, sort: 'matches' } : undefined);
  const results = (search.length >= 2 ? (query.data?.items ?? []) : []).filter(
    (p) => !excludeIds.includes(p.id),
  );
  const expanded = open && search.length >= 2;

  const choose = (player: PlayerListItem | undefined) => {
    if (!player) return;
    onSelect(player);
    setTerm('');
    setOpen(false);
    setActive(0);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(i + 1, Math.max(results.length - 1, 0)));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      choose(results[active]);
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div className={cn('relative flex min-w-56 flex-col gap-1', className)}>
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground"
        />
        <Input
          id={id}
          role="combobox"
          aria-expanded={expanded}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={expanded && results[active] ? `${listId}-${results[active].id}` : undefined}
          autoComplete="off"
          className="pl-8"
          placeholder={placeholder}
          value={term}
          onChange={(e) => {
            setTerm(e.target.value);
            setOpen(true);
            setActive(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKeyDown}
        />
      </div>
      {expanded && (
        <ul
          id={listId}
          role="listbox"
          aria-label={`${label} results`}
          className="absolute top-full z-20 mt-1 max-h-72 w-full min-w-64 overflow-y-auto rounded-md border bg-popover p-1 text-popover-foreground shadow-md"
        >
          {query.isFetching && results.length === 0 && (
            <li className="px-2 py-1.5 text-sm text-muted-foreground">Searching…</li>
          )}
          {!query.isFetching && results.length === 0 && (
            <li className="px-2 py-1.5 text-sm text-muted-foreground">No players found</li>
          )}
          {results.map((player, index) => (
            <li
              key={player.id}
              id={`${listId}-${player.id}`}
              role="option"
              aria-selected={index === active}
              className={cn(
                'flex cursor-pointer items-center justify-between gap-3 rounded-sm px-2 py-1.5 text-sm',
                index === active && 'bg-accent text-accent-foreground',
              )}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(player)}
            >
              <span className="font-medium">{player.name}</span>
              <span className="text-xs text-muted-foreground">
                {player.team?.code ?? ''} {player.firstSeason}–{player.lastSeason}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
