import { requiresFreshSnapshot, type ActionRequest } from './action.js'
import { loadV159Catalog } from './knowledge/catalog.js'
import { AdviceHistory } from './reasoning/advice-history.js'
import { ResilientReasoner } from './reasoning/resilient-reasoner.js'
import type { StreamingReasoningClient } from './reasoning/types.js'
import { suggestBlueprintActions } from './reasoning/suggestion-engine.js'
import type { GameSnapshot } from './snapshot.js'

interface LiveContext {
  summary: string
  snapshot: { gameRunning: boolean, wave: { number: number }, snapshotId: string, gameTick: number }
  actionReference: { refSnapshotId: string, refTick: number }
}

/** A controllable provider double that deliberately exceeds the 3.5-second limit. */
class SlowStreamingClient implements StreamingReasoningClient {
  async *stream({ signal }: { topic: string, prompt: string, signal: AbortSignal }): AsyncIterable<string> {
    await abortableDelay(4_000, signal)
    yield 'This text must never be returned because the request should time out first.'
  }
}

async function runAiLoopTest(): Promise<void> {
  console.log('🤖 Testing AIRI reasoning loop\n')

  const response = await fetch('http://127.0.0.1:18232/v1/context')
  if (!response.ok)
    throw new Error(`Local bridge responded with HTTP ${response.status}. Start it with: pnpm --dir packages/bridge serve`)
  const context = await response.json() as LiveContext
  console.log('📥 Game context:', context.summary)
  console.log('⏱️ Reference:', context.actionReference)

  const catalog = loadV159Catalog()
  const duo = catalog.getBlock('duo')
  if (duo === undefined)
    throw new Error('Offline catalog did not contain the Duo turret.')
  console.log('\n📚 Duo construction requirements:', duo.requirements)
  const starterBlueprint = catalog.getBlueprint('starter-duo-defense')
  if (starterBlueprint === undefined || starterBlueprint.requiredItems.some(item => item.itemId !== 'copper' || item.amount > 70))
    throw new Error('The curated starter blueprint must be present and affordable at the recommendation threshold.')

  const deterministicCandidate = suggestBlueprintActions(defenseExposedFixture())
  if (deterministicCandidate.length !== 1 || deterministicCandidate[0].type !== 'PLACE_BLUEPRINT')
    throw new Error('Defense exposure with 70 copper must produce exactly one blueprint candidate.')
  console.log('\n🛡️ Deterministic defense candidate:', deterministicCandidate[0])

  const proposal: ActionRequest = {
    version: 1,
    id: `proposal-wave-${context.snapshot.wave.number}`,
    idempotencyKey: `defense-wave-${context.snapshot.wave.number}-${context.actionReference.refSnapshotId}`,
    type: 'PLACE_BLUEPRINT',
    refSnapshotId: context.actionReference.refSnapshotId,
    refTick: context.actionReference.refTick,
    requiresConfirmation: true,
    target: { zoneId: 'zone-4-1', x: 180, y: 90 },
    payload: { schematicId: 'starter-duo-defense' },
  }
  console.log('\n📤 Blueprint proposal (not executed):', JSON.stringify(proposal, null, 2))
  if (!requiresFreshSnapshot(proposal))
    throw new Error('PLACE_BLUEPRINT must require a fresh snapshot.')
  if (!context.snapshot.gameRunning)
    console.log('⚠️ No active match: this proposal is intentionally informational only and must not be submitted.')

  const startedAt = Date.now()
  const history = new AdviceHistory()
  const reasoner = new ResilientReasoner(new SlowStreamingClient(), history)
  const outcome = await reasoner.reason({
    topic: 'timeout-resilience-test',
    prompt: 'This simulated provider deliberately responds too slowly.',
    snapshot: { snapshotId: context.snapshot.snapshotId, gameTick: context.snapshot.gameTick },
    source: 'player-request',
  })
  const elapsedMs = Date.now() - startedAt
  console.log(`\n⏲️ Timeout outcome after ${elapsedMs}ms:`, outcome)
  if (outcome.kind !== 'timed-out' || elapsedMs > 3_900)
    throw new Error('Slow reasoning did not abort within the expected timeout window.')
  if (history.list().length !== 0)
    throw new Error('Timed-out notification text must not be retained as completed advice.')

  console.log('\n✅ Live context, offline lookup, freshness policy, and timeout fallback all passed.')
}

function defenseExposedFixture(): GameSnapshot {
  return {
    schemaVersion: 2,
    snapshotId: 'fixture-defense-exposed',
    gameTick: 1_000,
    gameRunning: true,
    mapName: 'fixture-map',
    wave: { number: 3, timeRemainingTicks: 300 },
    players: { count: 1 },
    units: { friendly: 0, enemy: 2 },
    core: { health: 1_000, maxHealth: 1_000, inventory: [{ name: 'copper', amount: 70 }] },
    power: { networkCount: 0, produced: 0, needed: 0, stored: 0, capacity: 0 },
    factories: [],
    resources: { critical: [] },
    zones: [{ id: 'zone-4-1', role: 'core', buildingCount: 1, defenseCount: 0, productionCount: 0, damagedBuildingCount: 0, enemyUnitCount: 2, priority: 1_000 }],
    alerts: [{ kind: 'defense-exposed', severity: 'warning', zoneId: 'zone-4-1', message: 'Fixture threat.' }],
  }
}

function abortableDelay(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, milliseconds)
    signal.addEventListener('abort', () => {
      clearTimeout(timer)
      reject(new Error('simulated provider aborted'))
    }, { once: true })
  })
}

runAiLoopTest().catch(error => {
  console.error(error)
  process.exitCode = 1
})
