/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, type Medication, type Page } from './api'

export function formatDate(value: string | null | undefined, withTime = false): string {
  if (!value) return 'Unavailable'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unavailable'
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    ...(withTime ? { timeStyle: 'short' } : {}),
  }).format(date)
}

export function relativeDate(value: string): string {
  const delta = new Date(value).getTime() - Date.now()
  const minutes = Math.round(delta / 60_000)
  if (Math.abs(minutes) < 60) return new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 48) return new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(hours, 'hour')
  return formatDate(value)
}

export function humanize(value: string | null | undefined): string {
  if (!value) return 'Unavailable'
  return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase())
}

export function StatusBadge({ value }: { value: string }) {
  return <span className={`badge badge--${value.toLowerCase()}`}>{humanize(value)}</span>
}

export function MedicationStatuses({ medication }: { medication: Medication }) {
  if (medication.shortageStatuses.length === 0) return <span className="muted-value">No attached status</span>
  return <span className="badge-row">{medication.shortageStatuses.map(status => <StatusBadge key={status} value={status} />)}</span>
}

export function PageHeading({ eyebrow, title, description, actions }: {
  eyebrow: string
  title: string
  description: string
  actions?: ReactNode
}) {
  return <header className="page-heading">
    <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>
    {actions && <div className="page-heading__actions">{actions}</div>}
  </header>
}

export function LoadingBlock({ label = 'Loading RxRelay data' }: { label?: string }) {
  return <div className="loading-block" role="status"><span className="spinner" /><span>{label}</span></div>
}

export function EmptyState({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return <div className="empty-state"><span className="empty-state__mark" aria-hidden="true">◇</span><h2>{title}</h2><p>{body}</p>{action}</div>
}

export function ErrorState({ error, onRetry, compact = false }: { error: unknown; onRetry?: () => void; compact?: boolean }) {
  const apiError = error instanceof ApiError ? error : null
  const offline = apiError?.status === 0
  const limited = apiError?.status === 429
  return <div className={`error-state ${compact ? 'error-state--compact' : ''}`} role="alert">
    <span className="error-state__icon" aria-hidden="true">!</span>
    <div><strong>{offline ? 'RxRelay is offline' : limited ? 'Request limit reached' : 'This data could not be loaded'}</strong>
      <p>{limited ? 'The gateway is protecting the service. Wait briefly before retrying.' : apiError?.message ?? 'An unexpected request error occurred.'}</p>
      {apiError?.requestId && <small>Request ID: <code>{apiError.requestId}</code></small>}
    </div>
    {onRetry && <button className="button button--secondary" onClick={onRetry}>Try again</button>}
  </div>
}

export function Pagination({ page, label, onPage }: { page: Page<unknown>; label: string; onPage: (page: number) => void }) {
  if (page.totalPages <= 1) return null
  return <nav className="pagination" aria-label={`${label} pagination`}>
    <button disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>← Previous</button>
    <span>Page <strong>{page.page + 1}</strong> of <strong>{page.totalPages}</strong></span>
    <button disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>Next →</button>
  </nav>
}

export function DrugLink({ drug, compact = false }: { drug: Medication; compact?: boolean }) {
  return <Link to={`/medications/${drug.id}`} className={`drug-link ${compact ? 'drug-link--compact' : ''}`}>
    <span className="drug-mark" aria-hidden="true">{drug.name.slice(0, 2).toUpperCase()}</span>
    <span><strong>{drug.name}</strong><small>{drug.dosageForm ?? 'Dosage form unavailable'}</small></span>
  </Link>
}

export function Field({ label, value, mono = false }: { label: string; value: ReactNode; mono?: boolean }) {
  const unavailable = value === null || value === undefined || value === ''
  return <div className="field"><dt>{label}</dt><dd className={mono ? 'mono' : ''}>{unavailable ? <span className="unavailable">Not provided</span> : value}</dd></div>
}

export function CopyableId({ value, label = 'identifier' }: { value: string; label?: string }) {
  return <button className="copy-id" title={`Copy ${label}`} onClick={() => navigator.clipboard?.writeText(value)}><code>{value}</code></button>
}
