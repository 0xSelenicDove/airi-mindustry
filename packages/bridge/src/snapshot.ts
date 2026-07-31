/** A validated, read-only snapshot returned by the local Mindustry plugin. */
export interface GameSnapshot {
  /** Whether the server is currently hosting a game. */
  gameRunning: boolean
  /** Name of the active map, if the server has loaded one. */
  mapName: string | null
  /** Current survival wave number. */
  wave: number
  /** Remaining time before the next wave, in game ticks. */
  waveTime: number
  /** Players connected to the local server. */
  playerCount: number
  /** Names of the players currently connected to the local server. */
  players: string[]
  /** Units currently tracked by the server. */
  unitCount: number
  /** Units whose team differs from the default player team. */
  enemyUnitCount: number
  /** Shared default-team core state, if the team still has a core. */
  core: CoreSnapshot | null
}

/** Resources held in the default team's shared core inventory. */
export interface InventoryEntry {
  /** Mindustry item identifier, such as `copper`. */
  name: string
  /** Number of units stored in the core. */
  amount: number
}

/** Health and storage state for AIRI's local player team. */
export interface CoreSnapshot {
  /** Current core health. */
  health: number
  /** Core health at full integrity. */
  maxHealth: number
  /** Non-empty items stored in the shared core inventory. */
  inventory: InventoryEntry[]
}

const stateUrl = process.env.MINDUSTRY_STATE_URL ?? 'http://127.0.0.1:18231/v1/state'

/** Reads and validates the local plugin snapshot before it can enter AIRI context. */
export async function getGameSnapshot(fetcher: typeof fetch = fetch): Promise<GameSnapshot> {
  const response = await fetcher(stateUrl)
  if (!response.ok)
    throw new Error(`Mindustry state bridge responded with HTTP ${response.status}.`)

  return parseGameSnapshot(await response.json())
}

function parseGameSnapshot(value: unknown): GameSnapshot {
  if (!isRecord(value))
    throw new TypeError('Mindustry state bridge returned a non-object JSON value.')

  const { gameRunning, mapName, wave, waveTime, playerCount, players, unitCount, enemyUnitCount, core } = value
  if (
    typeof gameRunning !== 'boolean'
    || (mapName !== null && typeof mapName !== 'string')
    || !isNonNegativeFiniteNumber(wave)
    || !isNonNegativeFiniteNumber(waveTime)
    || !isNonNegativeFiniteNumber(playerCount)
    || !isStringArray(players)
    || players.length !== playerCount
    || !isNonNegativeFiniteNumber(unitCount)
    || !isNonNegativeFiniteNumber(enemyUnitCount)
    || !isCoreSnapshot(core)
  ) {
    throw new TypeError('Mindustry state bridge returned an invalid game snapshot.')
  }

  return { gameRunning, mapName, wave, waveTime, playerCount, players, unitCount, enemyUnitCount, core }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isNonNegativeFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every(entry => typeof entry === 'string')
}

function isCoreSnapshot(value: unknown): value is CoreSnapshot | null {
  if (value === null)
    return true
  if (!isRecord(value))
    return false

  return isNonNegativeFiniteNumber(value.health)
    && isNonNegativeFiniteNumber(value.maxHealth)
    && value.maxHealth > 0
    && Array.isArray(value.inventory)
    && value.inventory.every(isInventoryEntry)
}

function isInventoryEntry(value: unknown): value is InventoryEntry {
  return isRecord(value) && typeof value.name === 'string' && isNonNegativeFiniteNumber(value.amount)
}
