import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { AppRoutes } from './App'
import type { Medication, Notification, Page, Watchlist } from './api'

const drug: Medication = {
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

const page = <T,>(items: T[], totalPages = items.length ? 1 : 0): Page<T> => ({
  items, page: 0, size: 20, totalElements: items.length, totalPages,
})

const emptyNotifications = page<Notification>([])

function response(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response
}

function defaultHandler(input: RequestInfo | URL): Promise<Response> {
  const url = String(input)
  if (url.includes('/notifications')) return Promise.resolve(response(emptyNotifications))
  if (url.includes('/overview')) return Promise.resolve(response({ trackedMedications: 0, trackedShortageRecords: 0, unreadNotifications: 0, recentChanges: [], recentlyUpdatedMedications: [], latestIngestionRun: null }))
  return Promise.resolve(response(page([])))
}

function renderRoute(route: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[route]}><AppRoutes /></MemoryRouter></QueryClientProvider>)
}

describe('RxRelay product flows', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn(defaultHandler)))
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  test('searches medication identities through the API', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async input => {
      const url = String(input)
      if (url.includes('/drugs')) return response(page([drug]))
      return defaultHandler(input)
    })
    renderRoute('/medications')
    fireEvent.change(screen.getByPlaceholderText(/lisdexamfetamine/i), { target: { value: 'lisdexamfetamine' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('Lisdexamfetamine Dimesylate')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('query=lisdexamfetamine'))).toBe(true))
  })

  test('shows real medication detail, source evidence, and timeline fields', async () => {
    vi.mocked(fetch).mockImplementation(async input => {
      const url = String(input)
      if (url.endsWith(`/drugs/${drug.id}`)) return response(drug)
      if (url.includes('/shortages')) return response(page([{ id: 's1', sourceRecordId: 'fda-key', source: 'openfda', status: 'CURRENT', sourceStatus: 'Current', sourceUpdateType: 'Reverified', availability: 'Available', reason: null, company: 'Teva', presentation: 'Capsule, 70 mg', packageNdc: '0480-3564-01', manufacturer: 'Teva', sourceUpdatedAt: '2026-08-17T00:00:00Z', initialPostingAt: '2023-07-14T00:00:00Z', firstSeenAt: '2026-08-22T00:00:00Z', lastSeenAt: '2026-08-23T00:00:00Z' }]))
      if (url.includes('/timeline')) return response(page([{ id: 't1', previousStatus: null, newStatus: 'CURRENT', detectedAt: '2026-08-23T00:00:00Z', source: 'openfda', eventId: 'event-1' }]))
      if (url.includes('/watchlists')) return response(page<Watchlist>([]))
      return defaultHandler(input)
    })
    renderRoute(`/medications/${drug.id}`)
    expect(await screen.findByRole('heading', { name: drug.name })).toBeInTheDocument()
    expect(screen.getByText('0480-3564-01')).toBeInTheDocument()
    expect(screen.getByText(/meaningful state changes/i)).toBeInTheDocument()
    expect(screen.getAllByText(/openfda/i).length).toBeGreaterThan(0)
  })

  test('creates a named watchlist', async () => {
    let created = false
    const list: Watchlist = { id: '22222222-2222-4222-8222-222222222222', name: 'Critical supply', itemCount: 0, createdAt: '2026-08-23T00:00:00Z', updatedAt: '2026-08-23T00:00:00Z', items: page([]) }
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.endsWith('/watchlists') && init?.method === 'POST') { created = true; return response(list, 201) }
      if (url.includes(`/watchlists/${list.id}`)) return response(list)
      if (url.includes('/watchlists')) return response(page(created ? [list] : []))
      return defaultHandler(input)
    })
    renderRoute('/watchlists')
    fireEvent.change(screen.getByPlaceholderText('New watchlist name'), { target: { value: 'Critical supply' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(await screen.findByRole('heading', { name: 'Critical supply' })).toBeInTheDocument()
  })

  test('adds a searched medication to a watchlist', async () => {
    let added = false
    const list: Watchlist = { id: '22222222-2222-4222-8222-222222222222', name: 'Critical supply', itemCount: 0, createdAt: '2026-08-23T00:00:00Z', updatedAt: '2026-08-23T00:00:00Z', items: page([]) }
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/drugs?')) return response(page([drug]))
      if (url.endsWith(`/watchlists/${list.id}/items`) && init?.method === 'POST') { added = true; return response({ id: 'item-1', drug, createdAt: '2026-08-23T00:00:00Z' }, 201) }
      if (url.includes(`/watchlists/${list.id}`)) return response({ ...list, itemCount: added ? 1 : 0, items: page(added ? [{ id: 'item-1', drug, createdAt: '2026-08-23T00:00:00Z' }] : []) })
      if (url.includes('/watchlists')) return response(page([{ ...list, itemCount: added ? 1 : 0 }]))
      return defaultHandler(input)
    })
    renderRoute('/watchlists')
    fireEvent.change(await screen.findByLabelText('Search medications to add'), { target: { value: 'lis' } })
    fireEvent.click(await screen.findByRole('button', { name: /lisdexamfetamine dimesylate/i }))
    expect((await screen.findAllByText('Capsule')).length).toBeGreaterThan(0)
  })

  test('displays an unread event-generated notification', async () => {
    const notification: Notification = { id: 'n1', drug, message: 'Lisdexamfetamine Dimesylate changed from RESOLVED to CURRENT', read: false, createdAt: '2026-08-23T18:00:00Z' }
    vi.mocked(fetch).mockImplementation(async input => String(input).includes('/notifications') ? response(page([notification])) : defaultHandler(input))
    renderRoute('/notifications')
    expect(await screen.findByText(notification.message)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mark read' })).toBeInTheDocument()
  })

  test('applies a status filter and paginates deterministically', async () => {
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async input => {
      if (String(input).includes('/drugs')) return response({ ...page([drug], 2), totalElements: 21 })
      return defaultHandler(input)
    })
    renderRoute('/medications')
    fireEvent.change(screen.getByLabelText('Shortage status'), { target: { value: 'CURRENT' } })
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('status=CURRENT'))).toBe(true))
    fireEvent.click(await screen.findByRole('button', { name: /next/i }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('page=1'))).toBe(true))
  })

  test('renders a useful backend-offline state', async () => {
    vi.mocked(fetch).mockImplementation(async input => {
      if (String(input).includes('/drugs')) throw new TypeError('Failed to fetch')
      return defaultHandler(input)
    })
    renderRoute('/medications')
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('RxRelay is offline')).toBeInTheDocument()
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
