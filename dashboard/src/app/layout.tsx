import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'KaAlerto LGU Dashboard',
  description: 'Flood reports and SOS requests for barangay officials',
};

// Light only, on purpose: no dark theme exists, and the browser must not invent one.
export const viewport: Viewport = { colorScheme: 'light', themeColor: '#ffffff' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
