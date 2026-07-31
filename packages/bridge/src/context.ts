import type { CoreSnapshot, GameSnapshot } from './snapshot.js'

/** A compact, opt-in payload intended for an AIRI extension to consume. */
export interface GameContext {
  /** Human-readable context suitable for a companion conversation. */
  summary: string
  /** Source snapshot retained for a UI or future tool call. */
  snapshot: GameSnapshot
}

/** Turns a validated snapshot into compact, deterministic conversation context. */
export function createGameContext(snapshot: GameSnapshot): GameContext {
  return { summary: summarizeGameSnapshot(snapshot), snapshot }
}

/** Creates the conversational summary from a validated game snapshot. */
export function summarizeGameSnapshot(snapshot: GameSnapshot): string {
  if (!snapshot.gameRunning)
    return 'Mindustry server is online, but no match is currently running.'

  const location = snapshot.mapName === null ? 'an unknown map' : snapshot.mapName
  const players = snapshot.players.length === 0 ? 'no connected players' : snapshot.players.join(', ')
  const enemies = snapshot.enemyUnitCount === 1 ? '1 enemy unit' : `${snapshot.enemyUnitCount} enemy units`
  return `On ${location}, wave ${snapshot.wave} is active with ${players}; ${enemies}; ${summarizeCore(snapshot.core)}.`
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
