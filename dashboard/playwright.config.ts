import { defineConfig, devices } from '@playwright/test';

// e2e/ mocks every Supabase call at the browser network layer (page.route), so this never
// touches the live table. No SUPABASE_URL/SUPABASE_ANON_KEY/DASHBOARD_PIN needed to run it.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3101',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 3101',
    url: 'http://localhost:3101',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
