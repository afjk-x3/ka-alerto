'use client';

import { useState, useEffect, useRef } from 'react';
import Logo from '@/components/Logo';

interface PinGateProps {
  wrongPin: boolean;
  /** The server's own "Too many attempts. Try again in Ns." (lib/dashboardAuth.ts), when that's why this shows. */
  message?: string | null;
  onSubmit: (pin: string) => void;
}

export default function PinGate({ wrongPin, message, onSubmit }: PinGateProps) {
  const [pin, setPin] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => inputRef.current?.focus(), []);

  return (
    <div className="gate">
      <form
        className="gate-card"
        onSubmit={(e) => {
          e.preventDefault();
          if (pin.trim()) onSubmit(pin.trim());
        }}
      >
        <div className="gate-mark">
          <Logo size={56} />
        </div>
        <h1>KaAlerto LGU Dashboard</h1>
        <p>Enter the shared PIN to view incoming flood reports and SOS requests.</p>
        <label htmlFor="pin" className="sr-only">PIN</label>
        <input
          id="pin"
          ref={inputRef}
          className={`gate-input ${wrongPin ? 'invalid' : ''}`}
          type="password"
          autoComplete="off"
          maxLength={16}
          value={pin}
          onChange={(e) => setPin(e.target.value)}
          placeholder="PIN"
          aria-invalid={wrongPin}
          aria-describedby="pin-error"
        />
        <div id="pin-error" className="gate-error" role="alert">
          {wrongPin ? (message ?? 'That PIN was not accepted. Try again.') : ''}
        </div>
        <button className="btn btn-primary btn-block" type="submit" disabled={!pin.trim()}>
          Open dashboard
        </button>
        <p className="gate-note">Demo access: one shared PIN, not personal accounts.</p>
      </form>
    </div>
  );
}
