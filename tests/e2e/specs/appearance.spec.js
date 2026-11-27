import {test, expect} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import AxeBuilder from '@axe-core/playwright';

test('site themes, home logo and avatar logout work across desktop and mobile', async ({page}, testInfo) => {
  test.setTimeout(180_000);
  await page.emulateMedia({colorScheme: 'dark'});
  await page.goto('/register');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByLabel('Full name', {exact: true}).fill('Appearance Verification');
  await page.getByLabel('Phone number', {exact: true}).fill('9'+String(Math.floor(Math.random()*1_000_000_000)).padStart(9, '0'));
  await page.getByLabel('Password', {exact: true}).fill('Verification-only-'+randomUUID()+'!9');
  await page.getByRole('button', {name: 'Create account', exact: true}).click();
  await expect(page).toHaveURL(/\/$/);
  const appearance = page.getByRole('combobox', {name: 'Appearance'});
  await appearance.selectOption('light');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await appearance.selectOption('dark');
  await page.reload();
  await expect(appearance).toHaveValue('dark');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

  for (const route of ['/', '/send', '/add-money', '/transactions', '/analytics', '/profile']) {
    await page.goto(route);
    await expect(page.getByRole('heading', {level: 1})).toBeVisible();
    const accessibility = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(accessibility.violations.map(issue => ({id: issue.id, targets: issue.nodes.map(node => node.target)}))).toEqual([]);
  }
  await page.getByRole('link', {name: 'WalletPay home', exact: true}).click();
  await expect(page).toHaveURL(/\/$/);
  await page.getByRole('button', {name: 'Account options'}).click();
  await expect(page.getByRole('button', {name: 'Sign out'})).toBeVisible();
  await page.screenshot({path: testInfo.outputPath('dark-account-menu.png'), fullPage: true});
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', {name: 'Account options'})).toBeFocused();
  await appearance.selectOption('system');
  await page.emulateMedia({colorScheme: 'light'});
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await page.emulateMedia({colorScheme: 'dark'});
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

  await page.setViewportSize({width: 390, height: 844});
  await page.goto('/profile');
  await page.getByRole('button', {name: 'Open navigation'}).click();
  await page.locator('.mobile-header').getByRole('link', {name: 'WalletPay home'}).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator('.sidebar')).not.toBeInViewport();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', {name: 'Open navigation'}).click();
  await page.getByRole('button', {name: 'Account options'}).click();
  await page.screenshot({path: testInfo.outputPath('mobile-dark-account-menu.png'), fullPage: true});
  await page.getByRole('button', {name: 'Sign out'}).click();
  await expect(page).toHaveURL(/\/login/);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  const loginAccessibility = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(loginAccessibility.violations.map(issue => issue.id)).toEqual([]);
});
