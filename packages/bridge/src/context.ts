import type { CoreSnapshot, FactoryEntry, GameAlert, GameSnapshot, PowerSnapshot } from './snapshot.js'

/** A compact, opt-in payload intended for an AIRI extension to consume. */
export interface GameContext {
  /** Human-readable context suitable for a companion conversation. */
  summary: string
  /** Source snapshot retained for a UI or future tool call. */
  snapshot: GameSnapshot
  /** Newly detected, notable changes since the bridge's prior snapshot. */
  events: GameEvent[]
  /** Required reference for any future action proposed from this context. */
  actionReference: { refSnapshotId: string, refTick: number }
}

/** A concise local-game event intended for a companion conversation. */
export interface GameEvent {
  /** Stable category for clients that need to group or filter alerts. */
  kind: 'wave-started' | 'enemy-pressure' | 'core-damaged' | 'power-shortage' | 'resource-low' | GameAlert['kind']
  /** Player-readable explanation of the detected change. */
  message: string
  /** Coarse local zone identifier when the event is spatially scoped. */
  zoneId?: string
}

/** Explains the read-only conditions the local bridge currently monitors. */
export const monitoredEventKinds = [
  'a new survival wave starts',
  'the enemy-unit count rises',
  'core integrity falls',
  'power production falls below demand',
  'a core resource drops to 100 or fewer units after decreasing',
] as const

/** Turns a validated snapshot into compact, deterministic conversation context. */
export function createGameContext(snapshot: GameSnapshot, previousSnapshot: GameSnapshot | null = null): GameContext {
  return {
    summary: summarizeGameSnapshot(snapshot),
    snapshot,
    events: detectGameEvents(snapshot, previousSnapshot),
    actionReference: { refSnapshotId: snapshot.snapshotId, refTick: snapshot.gameTick },
  }
}

/** Creates the conversational summary from a validated game snapshot. */
export function summarizeGameSnapshot(snapshot: GameSnapshot): string {
  if (!snapshot.gameRunning)
    return 'Mindustry client is running, but no match is currently active.'

  const location = snapshot.mapName === null ? 'an unknown map' : snapshot.mapName
  const players = snapshot.players.count === 1 ? '1 connected player' : `${snapshot.players.count} connected players`
  const enemies = snapshot.units.enemy === 1 ? '1 enemy unit' : `${snapshot.units.enemy} enemy units`
  const alerts = snapshot.alerts.length === 0 ? 'no active local alerts' : `${snapshot.alerts.length} active local alert${snapshot.alerts.length === 1 ? '' : 's'}`
  return `On ${location}, wave ${snapshot.wave.number} has ${formatAmount(snapshot.wave.timeRemainingTicks)} ticks remaining with ${players}; ${enemies}; ${summarizeCore(snapshot.core)}; ${alerts}.`
}

/** Produces a focused report of power health and the current production footprint. */
export function createFactoryReport(snapshot: GameSnapshot): string {
  if (!snapshot.gameRunning)
    return 'Mindustry client is running, but no match is currently active.'

  return `${summarizePower(snapshot.power)} ${summarizeFactories(snapshot.factories)}`
}

/** Detects meaningful changes without inferring intent or issuing game actions. */
export function detectGameEvents(snapshot: GameSnapshot, previousSnapshot: GameSnapshot | null): GameEvent[] {
  if (previousSnapshot === null || !snapshot.gameRunning)
    return []

  const events: GameEvent[] = []
  if (snapshot.wave.number > previousSnapshot.wave.number)
    events.push({ kind: 'wave-started', message: `Wave ${snapshot.wave.number} has started.` })
  if (snapshot.units.enemy > previousSnapshot.units.enemy && snapshot.units.enemy > 0)
    events.push({ kind: 'enemy-pressure', message: `${snapshot.units.enemy} enemy units are now active.` })

  const coreIntegrity = integrity(snapshot.core)
  const previousIntegrity = integrity(previousSnapshot.core)
  if (coreIntegrity !== null && previousIntegrity !== null && coreIntegrity < previousIntegrity)
    events.push({ kind: 'core-damaged', message: `Core integrity fell from ${previousIntegrity}% to ${coreIntegrity}%.` })
  if (snapshot.power.needed > 0 && snapshot.power.produced < snapshot.power.needed)
    events.push({ kind: 'power-shortage', message: `Power production (${formatAmount(snapshot.power.produced)}) is below demand (${formatAmount(snapshot.power.needed)}).` })

  for (const item of snapshot.core?.inventory ?? []) {
    const previousAmount = previousSnapshot.core?.inventory.find(previous => previous.name === item.name)?.amount ?? 0
    if (item.amount < previousAmount && item.amount <= 100)
      events.push({ kind: 'resource-low', message: `${item.name} is low at ${item.amount} in the core.` })
  }
  for (const alert of snapshot.alerts)
    events.push({ kind: alert.kind, message: alert.message, zoneId: alert.zoneId })
  return events
}

function summarizeCore(core: CoreSnapshot | null): string {
  if (core === null)
    return 'the default team has no core'

  const integrity = Math.round((core.health / core.maxHealth) * 100)
  const inventory = core.inventory.slice(0, 3).map(item => `${item.amount} ${item.name}`).join(', ')
  return inventory.length === 0
    ? `core integrity is ${integrity}% with no stored resources`
    : `core integrity is ${integrity}% with ${inventory}`
}

function summarizePower(power: PowerSnapshot): string {
  if (power.networkCount === 0)
    return 'No power network is currently detected.'

  const balance = power.produced - power.needed
  const status = balance < 0 ? 'shortfall' : 'surplus'
  const battery = power.capacity === 0 ? 'no battery storage' : `${Math.round((power.stored / power.capacity) * 100)}% battery charge`
  return `${power.networkCount} power network${power.networkCount === 1 ? '' : 's'}: ${formatAmount(power.produced)} produced, ${formatAmount(power.needed)} needed (${formatAmount(Math.abs(balance))} ${status}); ${battery}.`
}

function summarizeFactories(factories: FactoryEntry[]): string {
  if (factories.length === 0)
    return 'No production or crafting structures are currently detected.'

  const structures = [...factories]
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name))
    .slice(0, 5)
    .map(factory => `${factory.count} ${factory.name}`)
    .join(', ')
  return `Production footprint: ${structures}.`
}

function integrity(core: CoreSnapshot | null): number | null {
  return core === null ? null : Math.round((core.health / core.maxHealth) * 100)
}

function formatAmount(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}
