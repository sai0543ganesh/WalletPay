import {test, expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import AxeBuilder from '@axe-core/playwright';

const password = 'Verification-only-' + randomUUID() + '!9';
const phone = () => '9' + String(Math.floor(Math.random() * 1_000_000_000)).padStart(9, '0');

async function register(page, name, number) {
  await page.goto('/register');
  await page.getByLabel('Full name', {exact: true}).fill(name);
  await page.getByLabel('Phone number', {exact: true}).fill(number);
  await page.getByLabel('Password', {exact: true}).fill(password);
  await page.getByRole('button', {name: 'Create account', exact: true}).click();
  await expect(page).toHaveURL(/\/$/);
}

test('wallet journeys, ownership, export and mobile layout', async ({browser, page}, testInfo) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const alicePhone = phone();
  const bobPhone = phone();
  const bob = await browser.newContext();
  const bobPage = await bob.newPage();
  await register(bobPage, 'Browser Recipient', '+91'+bobPhone);
  await register(page, 'Browser Sender', '+91'+alicePhone);

  await page.goto('/add-money');
  await expect(page.getByText('Under development', {exact: true})).toBeVisible();
  await expect(page.getByLabel('Account number', {exact: true})).toBeDisabled();
  await expect(page.getByLabel('Amount', {exact: true})).toHaveCount(0);
  const claimResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/demo-funds/welcome') && response.request().method() === 'POST');
  await page.getByRole('button', {name: 'Claim ₹100 initial demo credit', exact: true}).click();
  const claim = await claimResponse;
  expect(claim.status()).toBe(201);
  expect(await claim.json()).toMatchObject({type: 'DEMO_CREDIT', status: 'COMPLETED', amountMinor: 10000});
  expect(await (await page.request.get('/api/v1/wallet')).json()).toMatchObject({balanceMinor: 10000});
  await page.reload();
  await expect(page.getByRole('button', {name: 'Claim ₹100 initial demo credit', exact: true})).toHaveCount(0);
  expect(await (await page.request.get('/api/v1/demo-funds')).json()).toMatchObject({enabled: true, welcomeAvailable: false});
  const fundingAccessibility = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(fundingAccessibility.violations.map(issue => ({id: issue.id, targets: issue.nodes.map(node => node.target)}))).toEqual([]);
  await page.screenshot({path: testInfo.outputPath('demo-credit-claimed.png'), fullPage: true});

  await page.goto('/send');
  await page.getByLabel('Recipient phone', {exact: true}).fill(alicePhone);
  await page.getByLabel('Amount', {exact: true}).fill('69.00');
  await page.getByRole('button', {name: 'Review transfer', exact: true}).click();
  await page.getByRole('button', {name: 'Confirm transfer', exact: true}).click();
  await expect(page.getByRole('alert')).toContainText('own wallet');
  expect(await (await page.request.get('/api/v1/wallet')).json()).toMatchObject({balanceMinor: 10000});
  await page.getByRole('button', {name: 'Edit details', exact: true}).click();
  await page.getByLabel('Recipient phone', {exact: true}).fill(bobPhone);
  await page.getByLabel('Amount', {exact: true}).fill('25.50');
  await page.getByLabel('Note', {exact: false}).fill('Browser verification transfer');
  await page.getByRole('button', {name: 'Review transfer', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Confirm transfer', exact: true})).toBeVisible();
  const transferResponse = page.waitForResponse(response => response.url().endsWith('/api/v1/transfers') && response.request().method() === 'POST');
  await page.getByRole('button', {name: 'Confirm transfer', exact: true}).click();
  const completedTransfer = await transferResponse;
  expect(completedTransfer.status()).toBe(201);
  const transfer = await completedTransfer.json();

  await page.goto('/transactions');
  const searchResponse = page.waitForResponse(response => response.url().includes('/api/v1/transactions?') && response.url().includes('search=Browser'));
  await page.getByLabel('Search transactions').fill('Browser verification');
  const filtered = await (await searchResponse).json();
  expect(filtered.items.some(item => item.id === transfer.id)).toBe(true);
  await expect(page.getByRole('link', {name: new RegExp(transfer.reference)})).toBeVisible();
  const download = page.waitForEvent('download');
  await page.getByRole('button', {name: 'Export CSV'}).click();
  expect((await download).suggestedFilename()).toMatch(/\.csv$/);
  await page.getByRole('link', {name: new RegExp(transfer.reference)}).click();
  await expect(page.getByText('Browser verification transfer', {exact: true})).toBeVisible();

  await page.goto('/analytics');
  await expect(page.getByRole('heading', {name: 'Wallet insights', exact: true})).toBeVisible();
  await page.goto('/profile');
  await page.getByLabel('Full name', {exact: true}).fill('Browser Sender Updated');
  await page.getByRole('button', {name: 'Save changes', exact: true}).click();
  await expect(page.getByText(/profile updated|saved/i).first()).toBeVisible();
  await page.goto('/');
  const accessibility = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(accessibility.violations.map(issue => ({id: issue.id, targets: issue.nodes.map(node => node.target)}))).toEqual([]);
  await page.screenshot({path: testInfo.outputPath('desktop-dashboard.png'), fullPage: true});

  await page.setViewportSize({width: 390, height: 844});
  await expect(page.locator('.sidebar')).not.toBeInViewport();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({path: testInfo.outputPath('mobile-dashboard.png'), fullPage: true});
  for (const route of ['/transactions', '/send', '/add-money', '/analytics', '/profile']) {
    await page.goto(route);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({path: testInfo.outputPath(`mobile-${route.slice(1)}.png`), fullPage: true});
  }
  await page.getByRole('button', {name: 'Open navigation', exact: true}).click();
  await page.getByRole('button', {name: 'Account options', exact: true}).click();
  await page.getByRole('button', {name: 'Sign out', exact: true}).click();
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel('Phone number', {exact: true}).fill('+91'+alicePhone);
  await page.getByLabel('Password', {exact: true}).fill(password);
  await page.getByRole('button', {name: 'Sign in', exact: true}).click();
  await expect(page).toHaveURL(/\/$/);
  expect(errors).toEqual([]);
  expect(await page.evaluate(() => Object.keys(localStorage).some(key => /token|jwt/i.test(key)))).toBe(false);
  await bob.close();
});

test('unauthenticated pages redirect and login validation remains accessible', async ({page}) => {
  await page.goto('/transactions');
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel('Phone number', {exact: true}).fill('9000000000');
  await page.getByLabel('Password', {exact: true}).fill('Incorrect-verification-only!9');
  await page.getByRole('button', {name: 'Sign in', exact: true}).click();
  await expect(page.getByRole('alert')).toBeVisible();
});

test('sign-in page has no detected WCAG A/AA violations', async ({page}) => {
  await page.goto('/login');
  await expect(page.getByLabel('Phone number', {exact: true})).toBeVisible();
  const result = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations.map(issue => ({id: issue.id, targets: issue.nodes.map(node => node.target)}))).toEqual([]);
});
