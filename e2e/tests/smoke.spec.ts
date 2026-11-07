import { expect, openNavigation, test } from '../support/fixtures'

test('a fresh user is signed in and sees the navigation', async ({ page, user }) => {
  await page.goto('/')
  await openNavigation(page)
  await expect(page.getByRole('link', { name: 'Kalender' })).toBeVisible()
  await expect(page.getByRole('button', { name: new RegExp(user.username) })).toBeVisible()
})
