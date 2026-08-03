import test from 'node:test'
import assert from 'node:assert/strict'
import { loadExtension } from './test-support.mjs'

test('repair status tool reports only actual gateway state', async () => {
  const fixture = await loadExtension({ responses: { '/v1/repair/tasks/repair-1': { body: {
    taskId: 'repair-1', taskStatus: 'REPAIRING', gameTick: 120, airiControlReleased: false,
  } } } })
  try {
    const tool = fixture.tools.get('get_mindustry_repair_status')
    assert.equal((await tool.execute({ taskId: 'repair-1' })).taskStatus, 'REPAIRING')
  } finally { fixture.restore() }
})

test('audit tool supports what did you do queries with player-readable local history', async () => {
  const fixture = await loadExtension({ responses: { '/v1/audit': { body: { entries: [{ actionType: 'REPAIR_ZONE', status: 'COMPLETE' }] } } } })
  try {
    const tool = fixture.tools.get('get_mindustry_action_audit')
    assert.deepEqual((await tool.execute({ limit: 1 })).entries, [{ actionType: 'REPAIR_ZONE', status: 'COMPLETE' }])
  } finally { fixture.restore() }
})

test('emergency stop calls only the local stop endpoint', async () => {
  const fixture = await loadExtension({ responses: { '/v1/action/stop': { body: { stoppedTaskIds: ['repair-1'], count: 1 } } } })
  try {
    const tool = fixture.tools.get('stop_mindustry_actions')
    assert.ok(tool, 'emergency stop tool must be registered')
    assert.equal((await tool.execute()).count, 1)
    assert.equal(fixture.requests.at(-1).pathname, '/v1/action/stop')
  } finally { fixture.restore() }
})

test('blueprint suggestions remain informational and never submit an action', async () => {
  const fixture = await loadExtension({ responses: { '/v1/suggestions': { body: { candidates: [{ type: 'PLACE_BLUEPRINT' }], policy: 'confirmation required' } } } })
  try {
    await fixture.tools.get('get_mindustry_blueprint_suggestions').execute()
    assert.deepEqual(fixture.requests.map(request => request.pathname), ['/v1/suggestions'])
  } finally { fixture.restore() }
})
