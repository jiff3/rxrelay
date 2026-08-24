import { FormEvent, useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api, type Medication, type Watchlist } from './api'
import {
  CopyableId,
  DrugLink,
  EmptyState,
  ErrorState,
  Field,
  formatDate,
  humanize,
  LoadingBlock,
  MedicationStatuses,
  PageHeading,
  Pagination,
  relativeDate,
  StatusBadge,
} from './components'

export function OverviewPage() {
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.overview })
  if (overview.isLoading) return <PageFrame><LoadingBlock label="Loading persisted system overview" /></PageFrame>
  if (overview.isError) return <PageFrame><ErrorState error={overview.error} onRetry={() => overview.refetch()} /></PageFrame>
  const data = overview.data!
  return <>
    <section className="overview-hero">
      <div className="overview-hero__copy"><p className="eyebrow">Medication supply intelligence</p><h1>Public shortage data,<br /><em>made traceable.</em></h1><p>Follow normalized FDA shortage records from ingestion through status history, watchlists, and event-driven notifications.</p><div className="hero-actions"><Link className="button" to="/medications">Explore medications</Link><Link className="text-link" to="/activity">Inspect event processing →</Link></div></div>
      <div className="signal-card"><span className={`pulse pulse--${data.latestIngestionRun?.status.toLowerCase() ?? 'idle'}`} /><p>Latest ingestion</p><strong>{data.latestIngestionRun ? humanize(data.latestIngestionRun.status) : 'No run recorded'}</strong><small>{data.latestIngestionRun ? `${data.latestIngestionRun.source} · ${relativeDate(data.latestIngestionRun.startedAt)}` : 'Run ingestion to populate current public records.'}</small></div>
    </section>
    <PageFrame className="overview-content">
      <section className="metric-strip" aria-label="Current persisted totals">
        <Metric value={data.trackedShortageRecords} label="Shortage records" detail="Current source records in PostgreSQL" />
        <Metric value={data.trackedMedications} label="Medication identities" detail="Normalized and unresolved identities" />
        <Metric value={data.unreadNotifications} label="Unread notifications" detail="For the configured demo user" />
      </section>
      <div className="overview-grid">
        <section className="surface overview-changes"><SectionTitle eyebrow="Change ledger" title="Recent availability transitions" link="/activity" />
          {data.recentChanges.length === 0 ? <EmptyState title="No transitions recorded" body="A meaningful status change will appear after ingestion detects one." /> : <div className="change-list">{data.recentChanges.map(change => <article key={change.id} className="change-row"><DrugLink drug={change.drug} compact /><div className="transition"><StatusBadge value={change.previousStatus ?? 'NEW'} /><span aria-hidden="true">→</span><StatusBadge value={change.newStatus} /></div><div className="row-meta"><span>{change.source}</span><time dateTime={change.occurredAt}>{relativeDate(change.occurredAt)}</time></div></article>)}</div>}
        </section>
        <aside className="surface"><SectionTitle eyebrow="Freshness" title="Latest ingestion run" />
          {data.latestIngestionRun ? <IngestionSummary run={data.latestIngestionRun} /> : <EmptyState title="No ingestion history" body="Start a real ingestion run to establish freshness and source counters." />}
        </aside>
      </div>
      <section className="surface updated-section"><SectionTitle eyebrow="Recently refreshed" title="Medication identities" link="/medications?sort=updatedAt%2Cdesc" />
        {data.recentlyUpdatedMedications.length === 0 ? <EmptyState title="No medication data" body="RxRelay will show real normalized identities after a successful ingestion." /> : <div className="updated-grid">{data.recentlyUpdatedMedications.map(drug => <article className="updated-card" key={drug.id}><DrugLink drug={drug} /><MedicationStatuses medication={drug} /><time dateTime={drug.updatedAt}>Updated {relativeDate(drug.updatedAt)}</time></article>)}</div>}
      </section>
    </PageFrame>
  </>
}

function Metric({ value, label, detail }: { value: number; label: string; detail: string }) {
  return <article><strong>{value.toLocaleString()}</strong><span>{label}</span><small>{detail}</small></article>
}

function SectionTitle({ eyebrow, title, link }: { eyebrow: string; title: string; link?: string }) {
  return <header className="section-title"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div>{link && <Link to={link}>View all →</Link>}</header>
}

function IngestionSummary({ run }: { run: NonNullable<Awaited<ReturnType<typeof api.overview>>['latestIngestionRun']> }) {
  return <div className="ingestion-summary"><div className="ingestion-summary__state"><StatusBadge value={run.status} /><time dateTime={run.startedAt}>{formatDate(run.startedAt, true)}</time></div><dl><Field label="Source" value={run.source} /><Field label="Fetched" value={run.fetched?.toLocaleString()} /><Field label="Published" value={run.published?.toLocaleString()} /><Field label="Malformed" value={run.malformed?.toLocaleString()} /></dl><p>Run ID <CopyableId value={run.id} label="ingestion run ID" /></p></div>
}

export function ExplorerPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const status = searchParams.get('status') ?? ''
  const manufacturer = searchParams.get('manufacturer') ?? ''
  const sort = searchParams.get('sort') ?? 'name,asc'
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const [draft, setDraft] = useState(query)
  const result = useQuery({ queryKey: ['drugs', query, status, manufacturer, sort, page], queryFn: () => api.drugs({ query, status, manufacturer, sort, page, size: 20 }), placeholderData: previous => previous })

  const update = (values: Record<string, string | number>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(values).forEach(([key, value]) => value === '' || value === 0 ? next.delete(key) : next.set(key, String(value)))
    if (!('page' in values)) next.delete('page')
    setSearchParams(next)
  }
  const submit = (event: FormEvent) => { event.preventDefault(); update({ query: draft, page: 0 }) }
  return <PageFrame>
    <PageHeading eyebrow="Medication explorer" title="Search the supply index" description="Browse normalized identities and filter by current source-record status. Results are bounded and deterministically sorted." />
    <form className="filter-bar" onSubmit={submit} role="search">
      <label className="search-control"><span>Medication name</span><div><span aria-hidden="true">⌕</span><input value={draft} onChange={event => setDraft(event.target.value)} placeholder="e.g. lisdexamfetamine" /></div></label>
      <label><span>Shortage status</span><select aria-label="Shortage status" value={status} onChange={event => update({ status: event.target.value, page: 0 })}><option value="">All statuses</option><option value="CURRENT">Current</option><option value="RESOLVED">Resolved</option><option value="TO_BE_DISCONTINUED">To be discontinued</option><option value="UNKNOWN">Unknown</option></select></label>
      <label><span>Manufacturer</span><input aria-label="Manufacturer" value={manufacturer} onChange={event => update({ manufacturer: event.target.value, page: 0 })} placeholder="Any manufacturer" /></label>
      <label><span>Sort by</span><select aria-label="Sort medications" value={sort} onChange={event => update({ sort: event.target.value, page: 0 })}><option value="name,asc">Name A–Z</option><option value="name,desc">Name Z–A</option><option value="updatedAt,desc">Recently updated</option><option value="updatedAt,asc">Oldest updated</option></select></label>
      <button className="button" type="submit">Search</button>
    </form>
    {result.isError && <ErrorState error={result.error} onRetry={() => result.refetch()} />}
    {result.isLoading && <LoadingBlock label="Searching medication identities" />}
    {result.data && <section className={`surface table-surface ${result.isFetching ? 'is-refreshing' : ''}`} aria-busy={result.isFetching}>
      <div className="result-summary"><p><strong>{result.data.totalElements.toLocaleString()}</strong> medication {result.data.totalElements === 1 ? 'identity' : 'identities'}</p>{result.isFetching && <span>Refreshing…</span>}</div>
      {result.data.items.length === 0 ? <EmptyState title="No medications found" body="Try a broader name, remove a filter, or run ingestion if no data has been loaded." /> : <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Medication</th><th>Current source statuses</th><th>RxNorm</th><th>Last normalized</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{result.data.items.map(drug => <tr key={drug.id}><td><DrugLink drug={drug} compact /></td><td><MedicationStatuses medication={drug} /></td><td>{drug.rxCui ? <code>{drug.rxCui}</code> : <span className="muted-value">{humanize(drug.normalizationStatus)}</span>}</td><td><time dateTime={drug.updatedAt}>{formatDate(drug.updatedAt)}</time></td><td><Link className="row-action" aria-label={`View ${drug.name}`} to={`/medications/${drug.id}`}>→</Link></td></tr>)}</tbody></table></div>}
      <Pagination page={result.data} label="Medication results" onPage={next => update({ page: next })} />
    </section>}
  </PageFrame>
}

export function MedicationDetailPage() {
  const { drugId = '' } = useParams()
  const drug = useQuery({ queryKey: ['drug', drugId], queryFn: () => api.drug(drugId), enabled: !!drugId })
  const shortages = useQuery({ queryKey: ['shortages', drugId], queryFn: () => api.shortages(drugId), enabled: !!drugId })
  const timeline = useQuery({ queryKey: ['timeline', drugId], queryFn: () => api.timeline(drugId), enabled: !!drugId })
  if (drug.isLoading) return <PageFrame><LoadingBlock label="Loading medication details" /></PageFrame>
  if (drug.isError) return <PageFrame><ErrorState error={drug.error} onRetry={() => drug.refetch()} /></PageFrame>
  const medication = drug.data!
  return <PageFrame>
    <Link className="back-link" to="/medications">← Medication explorer</Link>
    <header className="drug-hero"><div><p className="eyebrow">Normalized medication identity</p><h1>{medication.name}</h1><p>{medication.sourceName && medication.sourceName !== medication.name ? <>Source name: <strong>{medication.sourceName}</strong></> : 'Canonical and source naming are the same.'}</p><MedicationStatuses medication={medication} /></div><WatchlistPicker drug={medication} /></header>
    <section className="identity-grid surface"><Field label="Canonical name" value={medication.name} /><Field label="Original source name" value={medication.sourceName} /><Field label="Generic name" value={medication.genericName} /><Field label="RxCUI" value={medication.rxCui} mono /><Field label="Dosage form" value={medication.dosageForm} /><Field label="Normalization status" value={humanize(medication.normalizationStatus)} /></section>
    <div className="detail-columns">
      <section className="surface"><SectionTitle eyebrow="Source evidence" title="Current shortage records" />{shortages.isLoading && <LoadingBlock />}{shortages.isError && <ErrorState error={shortages.error} compact />}{shortages.data?.items.length === 0 && <EmptyState title="No attached records" body="This identity exists, but no source shortage record is attached." />}{shortages.data?.items.map(record => <article className="shortage-record" key={record.id}><header><StatusBadge value={record.status} /><span>{record.source} · updated {formatDate(record.sourceUpdatedAt)}</span></header><h3>{record.presentation ?? 'Presentation not provided by FDA'}</h3><p className="availability-copy">{record.availability ?? 'Availability narrative not provided by FDA.'}</p><dl><Field label="Source status" value={record.sourceStatus} /><Field label="Update type" value={record.sourceUpdateType} /><Field label="Manufacturer" value={record.manufacturer ?? record.company} /><Field label="Package NDC" value={record.packageNdc} mono /><Field label="Shortage reason" value={record.reason} /><Field label="Initial posting" value={record.initialPostingAt ? formatDate(record.initialPostingAt) : null} /><Field label="Source record ID" value={<CopyableId value={record.sourceRecordId} label="source record ID" />} /><Field label="Last observed" value={formatDate(record.lastSeenAt, true)} /></dl></article>)}</section>
      <section className="surface timeline-panel"><SectionTitle eyebrow="Availability timeline" title="Meaningful state changes" />{timeline.isLoading && <LoadingBlock />}{timeline.isError && <ErrorState error={timeline.error} compact />}{timeline.data?.items.length === 0 && <EmptyState title="No transitions yet" body="Repeated identical observations are intentionally omitted." />}{timeline.data && <ol className="timeline">{timeline.data.items.map(event => <li key={event.id}><span className="timeline__node" /><div className="timeline__card"><time dateTime={event.detectedAt}>{formatDate(event.detectedAt, true)}</time><div className="transition"><StatusBadge value={event.previousStatus ?? 'NEW'} /><span>→</span><StatusBadge value={event.newStatus} /></div><p>Source: <strong>{event.source}</strong></p><CopyableId value={event.eventId} label="event ID" /></div></li>)}</ol>}</section>
    </div>
  </PageFrame>
}

function WatchlistPicker({ drug }: { drug: Medication }) {
  const client = useQueryClient()
  const lists = useQuery({ queryKey: ['watchlists'], queryFn: api.watchlists })
  const [requestedListId, setListId] = useState('')
  const listId = lists.data?.items.some(list => list.id === requestedListId) ? requestedListId : (lists.data?.items[0]?.id ?? '')
  const selected = lists.data?.items.find(list => list.id === listId)
  const details = useQuery({ queryKey: ['watchlist', listId], queryFn: () => api.watchlist(listId), enabled: !!listId })
  const existing = details.data?.items?.items.find(item => item.drug.id === drug.id)
  const add = useMutation({ mutationFn: () => api.addWatchlistItem(listId, drug.id), onSuccess: () => { client.invalidateQueries({ queryKey: ['watchlist', listId] }); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  const remove = useMutation({ mutationFn: () => api.removeWatchlistItem(listId, existing!.id), onSuccess: () => { client.invalidateQueries({ queryKey: ['watchlist', listId] }); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  if (lists.data?.items.length === 0) return <Link className="button" to="/watchlists">Create a watchlist</Link>
  return <div className="watch-control"><label><span>Watchlist</span><select aria-label="Choose watchlist" value={listId} onChange={event => setListId(event.target.value)}>{lists.data?.items.map(list => <option value={list.id} key={list.id}>{list.name}</option>)}</select></label><button className={`button ${existing ? 'button--secondary' : ''}`} disabled={!selected || details.isLoading || add.isPending || remove.isPending} onClick={() => existing ? remove.mutate() : add.mutate()}>{existing ? 'Remove from watchlist' : 'Add to watchlist'}</button>{(add.error || remove.error) && <span className="inline-error">{(add.error ?? remove.error)?.message}</span>}</div>
}

export function WatchlistsPage() {
  const client = useQueryClient()
  const lists = useQuery({ queryKey: ['watchlists'], queryFn: api.watchlists })
  const [requestedId, setSelectedId] = useState('')
  const [name, setName] = useState('')
  const selectedId = lists.data?.items.some(list => list.id === requestedId) ? requestedId : (lists.data?.items[0]?.id ?? '')
  const detail = useQuery({ queryKey: ['watchlist', selectedId], queryFn: () => api.watchlist(selectedId), enabled: !!selectedId })
  const create = useMutation({ mutationFn: () => api.createWatchlist(name), onSuccess: value => { setName(''); setSelectedId(value.id); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  const removeList = useMutation({ mutationFn: api.deleteWatchlist, onSuccess: () => { setSelectedId(''); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  const removeItem = useMutation({ mutationFn: ({ listId, itemId }: { listId: string; itemId: string }) => api.removeWatchlistItem(listId, itemId), onSuccess: () => { client.invalidateQueries({ queryKey: ['watchlist', selectedId] }); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  const submit = (event: FormEvent) => { event.preventDefault(); if (name.trim()) create.mutate() }
  return <PageFrame><PageHeading eyebrow="Demo-user monitoring" title="Watchlists" description="Organize medications you want RxRelay to evaluate when meaningful supply changes arrive." actions={<form className="create-list" onSubmit={submit}><label className="sr-only" htmlFor="watchlist-name">Watchlist name</label><input id="watchlist-name" value={name} onChange={event => setName(event.target.value)} maxLength={100} placeholder="New watchlist name" /><button className="button" disabled={!name.trim() || create.isPending}>Create</button></form>} />
    {create.error && <ErrorState error={create.error} compact />}{lists.isError && <ErrorState error={lists.error} onRetry={() => lists.refetch()} />}{lists.isLoading && <LoadingBlock />}
    {lists.data?.items.length === 0 ? <EmptyState title="No watchlists yet" body="Create a named watchlist, then add medications from the explorer or within the list." /> : <div className="watchlist-layout"><aside className="watchlist-tabs" aria-label="Your watchlists">{lists.data?.items.map(list => <button key={list.id} className={selectedId === list.id ? 'active' : ''} onClick={() => setSelectedId(list.id)}><span>{list.name}</span><small>{list.itemCount} {list.itemCount === 1 ? 'medication' : 'medications'}</small></button>)}</aside><section className="surface watchlist-detail">{detail.isLoading && <LoadingBlock />}{detail.isError && <ErrorState error={detail.error} compact />}{detail.data && <><header><div><p className="eyebrow">Active watchlist</p><h2>{detail.data.name}</h2></div><button className="danger-link" onClick={() => { if (window.confirm(`Delete “${detail.data.name}”?`)) removeList.mutate(detail.data.id) }}>Delete watchlist</button></header><AddMedication list={detail.data} />{detail.data.items?.items.length === 0 ? <EmptyState title="This watchlist is empty" body="Search above to add a medication." /> : <div className="watch-items">{detail.data.items?.items.map(item => <article key={item.id}><DrugLink drug={item.drug} /><MedicationStatuses medication={item.drug} /><button aria-label={`Remove ${item.drug.name} from ${detail.data.name}`} onClick={() => removeItem.mutate({ listId: detail.data.id, itemId: item.id })}>Remove</button></article>)}</div>}</>}</section></div>}
  </PageFrame>
}

function AddMedication({ list }: { list: Watchlist }) {
  const client = useQueryClient()
  const [query, setQuery] = useState('')
  const search = useQuery({ queryKey: ['watchlist-drug-search', query], queryFn: () => api.drugs({ query, size: 6 }), enabled: query.trim().length >= 2 })
  const existing = useMemo(() => new Set(list.items?.items.map(item => item.drug.id) ?? []), [list.items])
  const add = useMutation({ mutationFn: (drugId: string) => api.addWatchlistItem(list.id, drugId), onSuccess: () => { setQuery(''); client.invalidateQueries({ queryKey: ['watchlist', list.id] }); client.invalidateQueries({ queryKey: ['watchlists'] }) } })
  return <div className="add-medication"><label><span>Add a medication</span><input aria-label="Search medications to add" value={query} onChange={event => setQuery(event.target.value)} placeholder="Type at least two characters" /></label>{search.isFetching && <span className="searching">Searching…</span>}{search.data && query.length >= 2 && <div className="search-popover">{search.data.items.length === 0 ? <p>No matching medications.</p> : search.data.items.map(drug => <button key={drug.id} disabled={existing.has(drug.id) || add.isPending} onClick={() => add.mutate(drug.id)}><span><strong>{drug.name}</strong><small>{drug.dosageForm ?? 'Form unavailable'}</small></span><span>{existing.has(drug.id) ? 'Added' : '+ Add'}</span></button>)}</div>}{add.error && <span className="inline-error">{add.error.message}</span>}</div>
}

export function NotificationsPage() {
  const client = useQueryClient()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const notifications = useQuery({ queryKey: ['notifications', unreadOnly, page], queryFn: () => api.notifications(unreadOnly, page) })
  const read = useMutation({ mutationFn: api.readNotification, onSuccess: () => { client.invalidateQueries({ queryKey: ['notifications'] }); client.invalidateQueries({ queryKey: ['overview'] }) } })
  return <PageFrame><PageHeading eyebrow="Signal inbox" title="Notifications" description="Real in-app notifications created by availability events for watchlisted medication identities." actions={<label className="toggle"><input type="checkbox" checked={unreadOnly} onChange={event => { setUnreadOnly(event.target.checked); setPage(0) }} /><span>Unread only</span></label>} />{notifications.isLoading && <LoadingBlock />}{notifications.isError && <ErrorState error={notifications.error} onRetry={() => notifications.refetch()} />}{notifications.data?.items.length === 0 && <EmptyState title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} body="Watch a medication and process a meaningful status change to create a notification." />}{notifications.data && <div className="notification-list">{notifications.data.items.map(item => <article key={item.id} className={item.read ? 'notification-card' : 'notification-card notification-card--unread'}><span className="notification-dot" aria-hidden="true" /><div><DrugLink drug={item.drug} compact /><p>{item.message}</p><time dateTime={item.createdAt}>{formatDate(item.createdAt, true)}</time></div><div>{item.read ? <span className="read-label">Read</span> : <button className="button button--secondary" onClick={() => read.mutate(item.id)}>Mark read</button>}</div></article>)}</div>}{notifications.data && <Pagination page={notifications.data} label="Notifications" onPage={setPage} />}</PageFrame>
}

export function ActivityPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const state = searchParams.get('state') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const events = useQuery({ queryKey: ['events', state, page], queryFn: () => api.events(state, page), placeholderData: previous => previous })
  const update = (nextState: string) => { const next = new URLSearchParams(); if (nextState) next.set('state', nextState); setSearchParams(next) }
  return <PageFrame><PageHeading eyebrow="System activity" title="Event inspector" description="Sanitized processing metadata from the durable idempotency ledger—without payloads, stack traces, or Kafka internals." actions={<label><span className="label-text">Processing state</span><select aria-label="Processing state" value={state} onChange={event => update(event.target.value)}><option value="">All states</option><option value="PROCESSED">Processed</option><option value="RETRYING">Retrying</option><option value="DEAD_LETTERED">Dead-lettered</option><option value="PROCESSING">Processing</option></select></label>} />{events.isLoading && <LoadingBlock />}{events.isError && <ErrorState error={events.error} onRetry={() => events.refetch()} />}{events.data?.items.length === 0 && <EmptyState title="No event records" body="Processed ingestion events will appear here after the Kafka consumer receives them." />}{events.data && events.data.items.length > 0 && <section className="surface table-surface"><div className="result-summary"><p><strong>{events.data.totalElements.toLocaleString()}</strong> recorded events</p>{events.isFetching && <span>Refreshing…</span>}</div><div className="data-table-wrap"><table className="data-table event-table"><thead><tr><th>Event</th><th>Producer</th><th>State</th><th>Entity</th><th>Received</th><th>Correlation</th><th /></tr></thead><tbody>{events.data.items.map(event => <tr key={event.eventId}><td><strong>{event.eventType}</strong><CopyableId value={event.eventId} label="event ID" /></td><td>{event.producer ?? 'Unavailable'}</td><td><StatusBadge value={event.processingState} />{event.retryCount > 0 && <small>{event.retryCount} retries</small>}</td><td><code>{event.sourceRecordId ?? event.ingestionRunId ?? '—'}</code></td><td><time dateTime={event.receivedAt}>{formatDate(event.receivedAt, true)}</time></td><td><code>{event.correlationId ?? '—'}</code></td><td><Link className="row-action" aria-label={`Inspect ${event.eventId}`} to={`/activity/${encodeURIComponent(event.eventId)}`}>→</Link></td></tr>)}</tbody></table></div><Pagination page={events.data} label="Event activity" onPage={next => { const params = new URLSearchParams(searchParams); params.set('page', String(next)); setSearchParams(params) }} /></section>}</PageFrame>
}

export function EventDetailPage() {
  const { eventId = '' } = useParams()
  const flow = useQuery({ queryKey: ['event-flow', eventId], queryFn: () => api.eventFlow(eventId), enabled: !!eventId })
  if (flow.isLoading) return <PageFrame><LoadingBlock label="Loading recorded event flow" /></PageFrame>
  if (flow.isError) return <PageFrame><ErrorState error={flow.error} onRetry={() => flow.refetch()} /></PageFrame>
  const { event, steps } = flow.data!
  return <PageFrame><Link className="back-link" to="/activity">← System activity</Link><PageHeading eyebrow="Event detail" title={event.eventType} description="This view includes only steps supported by persisted RxRelay records. Unrecorded internal operations are not inferred." actions={<StatusBadge value={event.processingState} />} /><section className="surface event-metadata"><dl><Field label="Event ID" value={<CopyableId value={event.eventId} label="event ID" />} /><Field label="Producer" value={event.producer} /><Field label="Schema version" value={event.schemaVersion} /><Field label="Source entity" value={event.sourceRecordId} mono /><Field label="Correlation ID" value={event.correlationId ? <CopyableId value={event.correlationId} label="correlation ID" /> : null} /><Field label="Source topic" value={event.sourceTopic} mono /><Field label="Retry count" value={event.retryCount.toString()} /><Field label="Last error code" value={event.lastErrorCode} /></dl></section><section className="surface flow-section"><SectionTitle eyebrow="Recorded path" title="Processing flow" />{steps.length === 0 ? <EmptyState title="No recorded steps" body="The event exists, but no lifecycle timestamps are available." /> : <ol className="event-flow">{steps.map((step, index) => <li key={`${step.code}-${index}`} className={step.state === 'FAILED' ? 'event-flow__failed' : ''}><span>{index + 1}</span><div><small>{step.code}</small><h3>{step.label}</h3><p>{step.detail ?? 'No additional recorded detail'}</p>{step.occurredAt ? <time dateTime={step.occurredAt}>{formatDate(step.occurredAt, true)}</time> : <time>Timestamp not recorded</time>}</div></li>)}</ol>}</section></PageFrame>
}

export function AboutPage() {
  return <PageFrame><PageHeading eyebrow="Data and purpose" title="About RxRelay" description="An open-source informational system for tracing public medication-shortage data—not a clinical or inventory decision tool." /><div className="prose-grid"><section className="surface prose"><h2>Public source data</h2><p>RxRelay ingests the FDA/openFDA drug-shortages endpoint. Source names, status text, dates, package NDCs, manufacturers, presentations, and availability narratives remain distinguishable from fields derived by RxRelay.</p><a href="https://open.fda.gov/apis/drug/drugshortages/" target="_blank" rel="noreferrer">openFDA drug shortages documentation ↗</a></section><section className="surface prose"><h2>Identity normalization</h2><p>A single source-provided RxCUI is accepted directly. Otherwise, NIH RxNorm exact/normalized matching is attempted. RxRelay records unresolved, ambiguous, and failed outcomes instead of forcing uncertain matches.</p><a href="https://lhncbc.nlm.nih.gov/RxNav/APIs/RxNormAPIs.html" target="_blank" rel="noreferrer">NIH RxNorm APIs ↗</a></section><section className="surface prose"><h2>Ingestion mechanism</h2><p>Runs are initiated manually through the FastAPI endpoint or CLI, or by an external scheduler. There is no hidden freshness claim: the latest completed run and its source counters appear on the overview page.</p></section><section className="surface prose prose--warning"><h2>Important limitations</h2><p>openFDA data is unvalidated public information and does not represent pharmacy-level stock. RxRelay does not predict duration, recommend substitutions, provide clinical guidance, or support patient or pharmacy accounts.</p><strong>Never use RxRelay for medical decision-making.</strong></section></div></PageFrame>
}

export function ArchitecturePage() {
  return <PageFrame><PageHeading eyebrow="System design" title="Architecture" description="A static view of the actual development topology. It is documentation, not a simulated live network map." /><section className="surface architecture"><div className="architecture__sources"><Node kind="external" title="openFDA" detail="Shortage records" /><Node kind="external" title="NIH RxNorm" detail="Identity resolution" /></div><Arrow label="bounded HTTP + retry" /><Node kind="python" title="Ingestion service" detail="Python · FastAPI · Pydantic" /><Arrow label="shortage observations" /><Node kind="kafka" title="Apache Kafka" detail="Single KRaft broker locally" /><Arrow label="at-least-once delivery" /><Node kind="java" title="Core service" detail="Spring MVC · JPA · outbox" /><div className="architecture__stores"><Node kind="database" title="PostgreSQL" detail="Authoritative state" /><Node kind="cache" title="Redis" detail="Disposable cache" /><Node kind="kafka" title="Outbound topics" detail="Changes + notifications" /></div><Arrow label="versioned REST API" direction="up" /><Node kind="gateway" title="Gateway" detail="Correlation + rate limit" /><Arrow label="HTTP" direction="up" /><Node kind="web" title="RxRelay web" detail="React · TypeScript · Query" /></section><div className="architecture-notes"><article><strong>Consistency</strong><p>Consumer state changes and idempotency markers commit in one PostgreSQL transaction.</p></article><article><strong>Outbound delivery</strong><p>The transactional outbox bridges database commit and Kafka publication safely.</p></article><article><strong>Cache posture</strong><p>Redis failures degrade performance, never the correctness of authoritative reads.</p></article></div></PageFrame>
}

function Node({ kind, title, detail }: { kind: string; title: string; detail: string }) { return <div className={`arch-node arch-node--${kind}`}><span aria-hidden="true" /><strong>{title}</strong><small>{detail}</small></div> }
function Arrow({ label, direction = 'down' }: { label: string; direction?: 'up' | 'down' }) { return <div className={`arch-arrow arch-arrow--${direction}`}><span>{label}</span><i aria-hidden="true">↓</i></div> }

export function NotFoundPage() {
  return <PageFrame><EmptyState title="Page not found" body="The requested RxRelay page does not exist." action={<Link className="button" to="/">Return to overview</Link>} /></PageFrame>
}

function PageFrame({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`page-frame ${className}`}>{children}</div>
}
