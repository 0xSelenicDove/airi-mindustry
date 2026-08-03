import test from 'node:test'
import assert from 'node:assert/strict'

test('real local bridge context and audit are readable when explicitly enabled', {
  skip: process.env.AIRI_MINDUSTRY_REAL_SMOKE === '1' ? false : 'Set AIRI_MINDUSTRY_REAL_SMOKE=1 with Mindustry and bridge running.',
}, async () => {
  const base = process.env.AIRI_MINDUSTRY_CONTEXT_URL ?? 'http://127.0.0.1:18232/v1/context'
  const context = await fetch(new URL('/v1/context', base))
  const audit = await fetch(new URL('/v1/audit', base))
  assert.equal(context.ok, true)
  assert.equal(audit.ok, true)
})
