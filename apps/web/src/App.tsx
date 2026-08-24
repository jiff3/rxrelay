import { useEffect, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BrowserRouter, NavLink, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { api } from './api'
import {
  AboutPage,
  ActivityPage,
  ArchitecturePage,
  EventDetailPage,
  ExplorerPage,
  MedicationDetailPage,
  NotFoundPage,
  NotificationsPage,
  OverviewPage,
  WatchlistsPage,
} from './pages'

const navigation = [
  { to: '/', label: 'Overview', icon: 'overview', end: true },
  { to: '/medications', label: 'Medications', icon: 'search', end: false },
  { to: '/watchlists', label: 'Watchlists', icon: 'bookmark', end: false },
  { to: '/notifications', label: 'Notifications', icon: 'bell', end: false },
  { to: '/activity', label: 'System activity', icon: 'activity', end: false },
] as const

export function App() {
  return <BrowserRouter><AppRoutes /></BrowserRouter>
}

export function AppRoutes() {
  return <><ScrollToTop /><Routes><Route element={<Layout />}>
    <Route index element={<OverviewPage />} />
    <Route path="medications" element={<ExplorerPage />} />
    <Route path="medications/:drugId" element={<MedicationDetailPage />} />
    <Route path="watchlists" element={<WatchlistsPage />} />
    <Route path="notifications" element={<NotificationsPage />} />
    <Route path="activity" element={<ActivityPage />} />
    <Route path="activity/:eventId" element={<EventDetailPage />} />
    <Route path="about" element={<AboutPage />} />
    <Route path="architecture" element={<ArchitecturePage />} />
    <Route path="*" element={<NotFoundPage />} />
  </Route></Routes></>
}

function Layout() {
  const unread = useQuery({ queryKey: ['notifications', true, 0], queryFn: () => api.notifications(true, 0) })
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <header className="topbar">
      <NavLink className="brand" to="/" aria-label="RxRelay overview"><span className="brand__mark">Rx</span><span className="brand__text"><strong>RxRelay</strong><small>Supply intelligence</small></span></NavLink>
      <nav className="desktop-nav" aria-label="Primary navigation">{navigation.map(item => <NavLink end={item.end} key={item.to} to={item.to} className={({ isActive }) => isActive ? 'active' : ''}><Icon name={item.icon} /><span>{item.label}</span>{item.to === '/notifications' && unread.data && unread.data.totalElements > 0 && <b>{unread.data.totalElements}</b>}</NavLink>)}</nav>
      <div className="utility-nav"><NavLink to="/architecture">Architecture</NavLink><NavLink to="/about">Data &amp; about</NavLink><span className="demo-chip">Local demo</span></div>
      <details className="mobile-menu"><summary aria-label="Open navigation"><Icon name="menu" /></summary><nav aria-label="Mobile navigation">{navigation.map(item => <NavLink end={item.end} key={item.to} to={item.to}>{item.label}</NavLink>)}<NavLink to="/architecture">Architecture</NavLink><NavLink to="/about">Data &amp; about</NavLink></nav></details>
    </header>
    <main id="main-content" tabIndex={-1}><Outlet /></main>
    <footer className="site-footer"><div><span className="brand__mark brand__mark--small">Rx</span><p><strong>RxRelay</strong><br />Open-source medication supply intelligence.</p></div><p>Informational public-data software.<br /><strong>Not for clinical decision-making.</strong></p><nav aria-label="Footer"><NavLink to="/about">Data sources</NavLink><NavLink to="/architecture">Architecture</NavLink></nav></footer>
  </div>
}

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo({ top: 0 }) }, [pathname])
  return null
}

function Icon({ name }: { name: string }) {
  const paths: Record<string, ReactNode> = {
    overview: <><path d="M4 5h6v6H4zM14 5h6v3h-6zM14 12h6v7h-6zM4 15h6v4H4z" /></>,
    search: <><circle cx="10.5" cy="10.5" r="5.5" /><path d="m15 15 4 4" /></>,
    bookmark: <path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.5L6 21z" />,
    bell: <><path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 8h18c0-1-3-1-3-8" /><path d="M10 21h4" /></>,
    activity: <path d="M3 12h4l2.5-6 5 12 2.5-6h4" />,
    menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  }
  return <svg className="icon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}
