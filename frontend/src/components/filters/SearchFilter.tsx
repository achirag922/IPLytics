import { Search } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Input, Label } from '@/components/ui/form';

interface SearchFilterProps {
  label: string;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  placeholder?: string;
  /** Pause after the last keystroke before the URL is updated. */
  delay?: number;
}

/** Free-text filter written to the URL once typing pauses (one request per search, not per keystroke). */
export function SearchFilter({ label, value, onChange, placeholder, delay = 350 }: SearchFilterProps) {
  const id = useId();
  const [text, setText] = useState(value ?? '');
  const [urlValue, setUrlValue] = useState(value);
  const [sent, setSent] = useState(value);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // Follow the URL when it changes from outside (back/forward, reset), but not when it echoes what was typed.
  if (value !== urlValue) {
    setUrlValue(value);
    if (value !== sent) {
      setText(value ?? '');
      setSent(value);
    }
  }

  useEffect(() => () => clearTimeout(timer.current), []);

  const onInput = (next: string) => {
    setText(next);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      const trimmed = next.trim() === '' ? undefined : next.trim();
      setSent(trimmed);
      onChange(trimmed);
    }, delay);
  };

  return (
    <div className="flex min-w-56 flex-col gap-1">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute left-2.5 top-2.5 size-4 text-muted-foreground"
        />
        <Input
          id={id}
          type="search"
          className="pl-8"
          value={text}
          maxLength={60}
          placeholder={placeholder}
          onChange={(e) => onInput(e.target.value)}
        />
      </div>
    </div>
  );
}
