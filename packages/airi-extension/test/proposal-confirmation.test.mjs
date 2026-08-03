import test from 'node:test'
import assert from 'node:assert/strict'
import { importProposalStore } from './test-support.mjs'

function proposal() {
  return { type: 'PLACE_BLUEPRINT', refSnapshotId: 's-1-t100', refTick: 100, target: { zoneId: 'zone-1' }, payload: { schematicId: 'starter-duo-defense' } }
}

test('prepare creates an immutable pending proposal', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  const action = proposal()
  const prepared = store.prepare(action, 100)
  action.payload.schematicId = 'changed-after-review'
  assert.equal(store.get(prepared.id).payload.schematicId, 'starter-duo-defense')
})

test('confirmation requires the exact pending proposal ID', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  assert.throws(() => store.confirm('unknown', 100), /unknown proposal/i)
})

test('confirmation rejects an expired proposal by game tick', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  const prepared = store.prepare(proposal(), 100)
  assert.throws(() => store.confirm(prepared.id, 281), /stale/i)
})

test('one proposal can be confirmed only once', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  const prepared = store.prepare(proposal(), 100)
  store.confirm(prepared.id, 101)
  assert.throws(() => store.confirm(prepared.id, 101), /already confirmed/i)
})

test('confirmation forwards only the reviewed payload', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  const prepared = store.prepare(proposal(), 100)
  const confirmed = store.confirm(prepared.id, 101)
  assert.deepEqual(confirmed.payload, { schematicId: 'starter-duo-defense' })
})

test('player cancellation clears pending proposal without submitting it', async () => {
  const { ProposalStore } = await importProposalStore()
  const store = new ProposalStore()
  const prepared = store.prepare(proposal(), 100)
  store.cancel(prepared.id)
  assert.equal(store.get(prepared.id), undefined)
})
