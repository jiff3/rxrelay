import { expect, test, type Route } from '@playwright/test'

const drug = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Lisdexamfetamine Dimesylate',
  genericName: 'Lisdexamfetamine Dimesylate Capsule',
  rxCui: '854836',
  dosageForm: 'Capsule',
  sourceName: 'Lisdexamfetamine Dimesylate Capsule',
  normalizationStatus: 'RESOLVED',
  shortageStatuses: ['CURRENT'],
  updatedAt: '2026-08-23T18:00:00Z',
}

const paged = (items: unknown[]) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 })

test('searches a medication, inspects provenance, watches it, and sees an event notification', async ({ page }) => {
  let listCreated = false
  let watched = false
  const list = { id: '22222222-2222-4222-8222-222222222222', name: 'Critical supply', itemCount: 0, createdAt: '2026-08-23T18:00:00Z', updatedAt: '2026-08-23T18:00:00Z' }
  const item = { id: '33333333-3333-4333-8333-333333333333', drug, createdAt: '2026-08-23T18:01:00Z' }

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (path === '/api/v1/notifications') {
      const notification = { id: 'n1', drug, message: `${drug.name} changed from RESOLVED to CURRENT`, read: false, createdAt: '2026-08-23T18:02:00Z' }
      return json(route, paged(watched ? [notification] : []))
    }
    if (path === '/api/v1/drugs') return json(route, paged([drug]))
    if (path === `/api/v1/drugs/${drug.id}`) return json(route, drug)
    if (path.endsWith('/shortages')) return json(route, paged([{ id: 's1', sourceRecordId: 'openfda-record-1', source: 'openfda', status: 'CURRENT', sourceStatus: 'Current', sourceUpdateType: 'Reverified', availability: 'Available', reason: null, company: 'Teva', presentation: 'Capsule, 70 mg', packageNdc: '0480-3564-01', manufacturer: 'Teva', sourceUpdatedAt: '2026-08-17T00:00:00Z', initialPostingAt: '2023-07-14T00:00:00Z', firstSeenAt: '2026-08-22T00:00:00Z', lastSeenAt: '2026-08-23T00:00:00Z' }]))
    if (path.endsWith('/timeline')) return json(route, paged([{ id: 't1', previousStatus: 'RESOLVED', newStatus: 'CURRENT', detectedAt: '2026-08-23T18:02:00Z', source: 'openfda', eventId: 'event-1' }]))
    if (path === '/api/v1/watchlists' && request.method() === 'POST') { listCreated = true; return json(route, { ...list, items: paged([]) }, 201) }
    if (path === '/api/v1/watchlists') return json(route, paged(listCreated ? [{ ...list, itemCount: watched ? 1 : 0 }] : []))
    if (path === `/api/v1/watchlists/${list.id}/items` && request.method() === 'POST') { watched = true; return json(route, item, 201) }
    if (path === `/api/v1/watchlists/${list.id}`) return json(route, { ...list, itemCount: watched ? 1 : 0, items: paged(watched ? [item] : []) })
    return json(route, paged([]))
  })

  await page.goto('/medications')
  await page.getByPlaceholder('e.g. lisdexamfetamine').fill('lisdexamfetamine')
  await page.getByRole('button', { name: 'Search' }).click()
  await page.getByRole('link', { name: /Lisdexamfetamine Dimesylate/ }).first().click()
  await expect(page.getByRole('heading', { name: drug.name })).toBeVisible()
  await expect(page.getByText('0480-3564-01')).toBeVisible()

  await page.getByRole('link', { name: 'Create a watchlist' }).click()
  await page.getByPlaceholder('New watchlist name').fill(list.name)
  await page.getByRole('button', { name: 'Create' }).click()
  await page.getByLabel('Search medications to add').fill('lisdexamfetamine')
  await page.getByRole('button', { name: /Lisdexamfetamine Dimesylate/ }).click()
  await expect(page.getByRole('link', { name: /Lisdexamfetamine Dimesylate/ })).toBeVisible()

  await page.getByRole('link', { name: 'Notifications' }).first().click()
  await expect(page.getByText(`${drug.name} changed from RESOLVED to CURRENT`)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Mark read' })).toBeVisible()
})

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}
