import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const webUrl = process.env.RXRELAY_SCREENSHOT_BASE_URL ?? 'http://127.0.0.1:5173'
const apiUrl = process.env.RXRELAY_API_BASE_URL ?? 'http://127.0.0.1:8080'
const output = resolve(import.meta.dirname, '../../../docs/screenshots')

const health = await fetch(`${apiUrl}/actuator/health/liveness`).catch(() => null)
const overview = await fetch(`${apiUrl}/api/v1/overview`).catch(() => null)
if (!health?.ok || !overview?.ok) {
  throw new Error(`RxRelay backend is not ready at ${apiUrl}; screenshots must use a running real backend.`)
}
const snapshot = await overview.json()
if (!Number.isFinite(snapshot.trackedShortageRecords) || snapshot.trackedShortageRecords < 1) {
  throw new Error('No persisted shortage records were found. Run real ingestion before capturing screenshots.')
}

await mkdir(output, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
for (const [name, path] of [['overview', '/'], ['medication-explorer', '/medications'], ['system-activity', '/activity']]) {
  await page.goto(`${webUrl}${path}`, { waitUntil: 'networkidle' })
  await page.screenshot({ path: resolve(output, `${name}.png`), fullPage: true })
}
await browser.close()
console.log(`Captured real-backend screenshots in ${output}`)
