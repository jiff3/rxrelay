import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const generated = join(root, 'src', 'generated', 'rxrelay-api.ts')
const temporaryDirectory = mkdtempSync(join(tmpdir(), 'rxrelay-openapi-'))
const candidate = join(temporaryDirectory, 'rxrelay-api.ts')
const cli = join(root, 'node_modules', 'openapi-typescript', 'bin', 'cli.js')

try {
  const result = spawnSync(
    process.execPath,
    [cli, '../../contracts/openapi/rxrelay-api.v1.yaml', '-o', candidate],
    { cwd: root, encoding: 'utf8' },
  )
  if (result.status !== 0) {
    process.stderr.write(result.stderr || result.stdout)
    process.exit(result.status ?? 1)
  }
  if (readFileSync(generated, 'utf8') !== readFileSync(candidate, 'utf8')) {
    console.error('Generated API types are stale. Run: npm run contract:generate')
    process.exit(1)
  }
  console.log('Frontend API types match contracts/openapi/rxrelay-api.v1.yaml')
} finally {
  rmSync(temporaryDirectory, { recursive: true, force: true })
}
