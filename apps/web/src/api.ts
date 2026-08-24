import type { components } from './generated/rxrelay-api'

export type Page<T> = components['schemas']['Page'] & { items: T[] }
export type Medication = components['schemas']['Drug']
export type Shortage = components['schemas']['Shortage']
export type TimelineEvent = components['schemas']['TimelineEvent']
export type WatchlistItem = components['schemas']['WatchlistItem']
export type Watchlist = components['schemas']['Watchlist']
export type Notification = components['schemas']['Notification']
export type IngestionRun = components['schemas']['IngestionRun']
export type RecentChange = components['schemas']['RecentChange']
export type Overview = components['schemas']['Overview']
export type ProcessedEvent = components['schemas']['ProcessedEvent']
export type EventStep = components['schemas']['EventStep']
export type EventFlow = components['schemas']['EventFlow']

interface ErrorBody {
  code?: string
  message?: string
  requestId?: string | null
  violations?: Array<{ field: string; message: string }>
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly requestId?: string | null,
    readonly violations: ErrorBody['violations'] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    })
  } catch (error) {
    throw new ApiError(
      error instanceof Error ? error.message : 'The backend could not be reached',
      0,
      'backend_offline',
    )
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ErrorBody | null
    const fallback = response.status === 429
      ? 'Request limit reached. Wait a moment and try again.'
      : `Request failed (${response.status})`
    throw new ApiError(
      body?.message ?? fallback,
      response.status,
      body?.code,
      body?.requestId,
      body?.violations,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function params(values: Record<string, string | number | boolean | null | undefined>): string {
  const result = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') result.set(key, String(value))
  })
  const query = result.toString()
  return query ? `?${query}` : ''
}

export const api = {
  overview: () => request<Overview>('/api/v1/overview'),
  drugs: (values: {
    query?: string
    status?: string
    manufacturer?: string
    page?: number
    size?: number
    sort?: string
  }) => request<Page<Medication>>(`/api/v1/drugs${params(values)}`),
  drug: (id: string) => request<Medication>(`/api/v1/drugs/${id}`),
  shortages: (id: string, page = 0) =>
    request<Page<Shortage>>(`/api/v1/drugs/${id}/shortages${params({ page, size: 50 })}`),
  timeline: (id: string, page = 0) =>
    request<Page<TimelineEvent>>(`/api/v1/drugs/${id}/timeline${params({ page, size: 50 })}`),
  watchlists: () => request<Page<Watchlist>>('/api/v1/watchlists?size=50'),
  watchlist: (id: string) => request<Watchlist>(`/api/v1/watchlists/${id}?itemSize=50`),
  createWatchlist: (name: string) => request<Watchlist>('/api/v1/watchlists', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }),
  deleteWatchlist: (id: string) => request<void>(`/api/v1/watchlists/${id}`, { method: 'DELETE' }),
  addWatchlistItem: (watchlistId: string, drugId: string) =>
    request<WatchlistItem>(`/api/v1/watchlists/${watchlistId}/items`, {
      method: 'POST',
      body: JSON.stringify({ drugId }),
    }),
  removeWatchlistItem: (watchlistId: string, itemId: string) =>
    request<void>(`/api/v1/watchlists/${watchlistId}/items/${itemId}`, { method: 'DELETE' }),
  notifications: (unreadOnly: boolean, page = 0) =>
    request<Page<Notification>>(`/api/v1/notifications${params({ unreadOnly, page, size: 20 })}`),
  readNotification: (id: string) =>
    request<Notification>(`/api/v1/notifications/${id}/read`, { method: 'PATCH' }),
  events: (state: string, page = 0) =>
    request<Page<ProcessedEvent>>(`/api/v1/system/events${params({ state, page, size: 20 })}`),
  eventFlow: (id: string) => request<EventFlow>(`/api/v1/system/events/${id}/flow`),
  ingestionRuns: () => request<Page<IngestionRun>>('/api/v1/system/ingestion-runs?size=10'),
}
