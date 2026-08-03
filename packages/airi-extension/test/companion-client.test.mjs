import test from 'node:test'
import assert from 'node:assert/strict'
import { loadExtension } from './test-support.mjs'

test('gets current Mindustry context from the loopback bridge', async () => {
  const fixture = await loadExtension({ responses: { '/v1/context': { body: { summary: 'Wave 4; core stable.' } } } })
  try {
    assert.deepEqual(await fixture.tools.get('get_mindustry_context').execute(), { summary: 'Wave 4; core stable.' })
    assert.deepEqual(fixture.requests.map(request => request.pathname), ['/v1/context'])
  } finally { fixture.restore() }
})

test('marks the context tool unavailable when the bridge is offline', async () => {
  const fixture = await loadExtension({ offline: true })
  try {
    assert.equal(await fixture.tools.get('get_mindustry_context').isAvailable(), false)
  } finally { fixture.restore() }
})

test('rejects malformed bridge context payloads', async () => {
  const fixture = await loadExtension({ responses: { '/v1/context': { body: { unexpected: true } } } })
  try {
    await assert.rejects(() => fixture.tools.get('get_mindustry_context').execute(), /invalid context payload/)
  } finally { fixture.restore() }
})

test('gets repair task status with its authoritative lifecycle tick', async () => {
  const fixture = await loadExtension({ responses: { '/v1/repair/tasks/repair-1': { body: {
    taskId: 'repair-1', taskStatus: 'TRAVELLING', gameTick: 101, airiControlReleased: false,
  } } } })
  try {
    const tool = fixture.tools.get('get_mindustry_repair_status')
    assert.ok(tool, 'repair status tool must be registered')
    assert.deepEqual(await tool.execute({ taskId: 'repair-1' }), {
      taskId: 'repair-1', taskStatus: 'TRAVELLING', gameTick: 101, airiControlReleased: false,
    })
  } finally { fixture.restore() }
})

test('surfaces TASK_NOT_FOUND for an unknown repair task', async () => {
  const fixture = await loadExtension({ responses: { '/v1/repair/tasks/missing': { ok: false, status: 404, body: { reasonCode: 'TASK_NOT_FOUND' } } } })
  try {
    const tool = fixture.tools.get('get_mindustry_repair_status')
    assert.ok(tool, 'repair status tool must be registered')
    await assert.rejects(() => tool.execute({ taskId: 'missing' }), /TASK_NOT_FOUND/)
  } finally { fixture.restore() }
})

test('gets newest-first audit pages using the local cursor', async () => {
  const fixture = await loadExtension({ responses: { '/v1/audit': { body: { entries: [{ sequence: 8 }, { sequence: 7 }], pageSize: 2 } } } })
  try {
    const tool = fixture.tools.get('get_mindustry_action_audit')
    assert.ok(tool, 'audit tool must be registered')
    const result = await tool.execute({ before: 9, limit: 2 })
    assert.equal(result.entries[0].sequence, 8)
    assert.match(fixture.requests.at(-1).href, /before=9/)
  } finally { fixture.restore() }
})
