import { test, expect } from '@playwright/test';
import { mockDashboard, SOS_EVENT, FLOOD_REPORT_EVENT } from './fixtures';

test('PIN gate blocks, then a PIN opens the dashboard with the right counts', async ({ page }) => {
  await mockDashboard(page, [SOS_EVENT, FLOOD_REPORT_EVENT]);
  await page.goto('/');

  await expect(page.getByPlaceholder('PIN')).toBeVisible();
  await page.getByPlaceholder('PIN').fill('1234');
  await page.getByRole('button', { name: 'Open dashboard' }).click();

  await expect(page.locator('.stat', { hasText: 'open SOS' }).locator('b')).toHaveText('1');
  await expect(page.locator('.stat', { hasText: 'flooded spots' }).locator('b')).toHaveText('1');
});

test('acknowledging an SOS writes the event and hides the button', async ({ page }) => {
  await mockDashboard(page, [SOS_EVENT]);
  await page.goto('/');
  await page.getByPlaceholder('PIN').fill('1234');
  await page.getByRole('button', { name: 'Open dashboard' }).click();

  await page.locator('.row-sos').first().click();
  const ack = page.getByRole('button', { name: /Acknowledge/ });
  await expect(ack).toBeVisible();
  await ack.click();

  await expect(ack).toBeHidden();
  await expect(page.getByText('Dashboard (official)')).toBeVisible();
});

test('the search filter narrows the report list', async ({ page }) => {
  await mockDashboard(page, [SOS_EVENT, FLOOD_REPORT_EVENT]);
  await page.goto('/');
  await page.getByPlaceholder('PIN').fill('1234');
  await page.getByRole('button', { name: 'Open dashboard' }).click();

  await page.getByRole('tab', { name: /Flood reports/ }).click();
  await expect(page.locator('.row-title', { hasText: 'Impassable for cars' })).toBeVisible();

  await page.getByPlaceholder('Search place, name, status…').fill('no such report');
  await expect(page.getByText('Nothing matches these filters.')).toBeVisible();
});
