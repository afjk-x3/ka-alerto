'use client';

import { useEffect, useId, useRef, useState } from 'react';

export interface ComboOption {
  value: string;
  /** A small right-aligned note, e.g. "3 shelters". */
  meta?: string;
}

interface ComboboxProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  /** What to offer right now; the parent decides (it knows what has been typed). */
  options: ComboOption[];
  placeholder?: string;
  /** Shown as a first row while the list is open and nothing is typed, e.g. "Type to search 1,634 places". */
  hint?: string;
  /** Shown when something is typed and nothing matches. */
  emptyText?: string;
  required?: boolean;
  maxLength?: number;
}

/** The matched part of an option in bold, so it is clear why it is offered. */
function Highlighted({ text, query }: { text: string; query: string }) {
  const q = query.trim().toLowerCase();
  const at = q ? text.toLowerCase().indexOf(q) : -1;
  if (at < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, at)}
      <mark>{text.slice(at, at + q.length)}</mark>
      {text.slice(at + q.length)}
    </>
  );
}

/**
 * A text field with a list of suggestions that opens the moment the field is clicked or focused (not only
 * after typing), filters as you type, and works from the keyboard: Down/Up move, Enter picks, Esc closes.
 * Anything typed is accepted as it is; the list only suggests. Follows the ARIA combobox pattern.
 */
export default function Combobox({ id, value, onChange, options, placeholder, hint, emptyText, required, maxLength }: ComboboxProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const listId = `${inputId}-list`;
  const wrap = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  // Close when focus or a click leaves the whole widget.
  useEffect(() => {
    if (!open) return;
    const away = (e: MouseEvent) => {
      if (!wrap.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', away);
    return () => document.removeEventListener('mousedown', away);
  }, [open]);

  // Keep the highlighted row in view while arrowing through a long list.
  useEffect(() => {
    if (active >= 0) document.getElementById(`${inputId}-opt-${active}`)?.scrollIntoView({ block: 'nearest' });
  }, [active, inputId]);

  const pick = (o: ComboOption) => {
    onChange(o.value);
    setOpen(false);
    setActive(-1);
  };

  const showList = open && (options.length > 0 || hint || (value.trim() && emptyText));

  return (
    <div className="combo" ref={wrap}>
      <input
        id={inputId}
        className="search"
        role="combobox"
        aria-expanded={Boolean(showList)}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={active >= 0 ? `${inputId}-opt-${active}` : undefined}
        autoComplete="off"
        value={value}
        placeholder={placeholder}
        required={required}
        maxLength={maxLength}
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setOpen(true);
            setActive((a) => (options.length ? (a + 1) % options.length : -1));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setActive((a) => (options.length ? (a <= 0 ? options.length - 1 : a - 1) : -1));
          } else if (e.key === 'Enter' && open && active >= 0 && options[active]) {
            e.preventDefault(); // pick, and do not submit the surrounding form
            pick(options[active]);
          } else if (e.key === 'Escape' && open) {
            e.stopPropagation();
            setOpen(false);
          } else if (e.key === 'Tab') {
            setOpen(false);
          }
        }}
      />
      <span className="combo-chevron" aria-hidden="true">▾</span>
      {showList && (
        <ul className="combo-list" id={listId} role="listbox">
          {hint && !value.trim() && <li className="combo-hint" role="presentation">{hint}</li>}
          {options.map((o, i) => (
            <li
              key={o.value}
              id={`${inputId}-opt-${i}`}
              role="option"
              aria-selected={i === active}
              className={`combo-opt ${i === active ? 'is-active' : ''}`}
              // mousedown, not click: the input must not lose focus before the choice registers.
              onMouseDown={(e) => {
                e.preventDefault();
                pick(o);
              }}
              onMouseEnter={() => setActive(i)}
            >
              <span><Highlighted text={o.value} query={value} /></span>
              {o.meta && <small>{o.meta}</small>}
            </li>
          ))}
          {options.length === 0 && value.trim() && emptyText && <li className="combo-hint" role="presentation">{emptyText}</li>}
        </ul>
      )}
    </div>
  );
}
