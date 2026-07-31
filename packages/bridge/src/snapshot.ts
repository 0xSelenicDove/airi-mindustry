/** A validated, aggregate-only snapshot returned by the local Mindustry plugin. */
export interface GameSnapshot {
  schemaVersion: 2
  /** Unique within the running client; actions must refer to this value. */
  snapshotId: string
  /** Authoritative Mindustry simulation tick at capture time. */
  gameTick: number
  gameRunning: boolean
  mapName: string | null
  wave: WaveSnapshot
  players: PlayerSummary
  units: UnitSummary
  core: CoreSnapshot | null
  power: PowerSnapshot
  factories: FactoryEntry[]
  resources: ResourceSummary
  /** Bounded coarse aggregates, never raw map tiles or building coordinates. */
  zones: ZoneSummary[]
  alerts: GameAlert[]
}

export interface WaveSnapshot {
  number: number
  timeRemainingTicks: number
}

export interface PlayerSummary { count: number }
export interface UnitSummary { friendly: number, enemy: number }

export interface InventoryEntry { name: string, amount: number }
export interface CoreSnapshot { health: number, maxHealth: number, inventory: InventoryEntry[] }
export interface PowerSnapshot { networkCount: number, produced: number, needed: number, stored: number, capacity: number }
export interface FactoryEntry { name: string, count: number }
export interface CriticalResource { item: string, amount: number, threshold: number }
export interface ResourceSummary { critical: CriticalResource[] }

export interface ZoneSummary {
  id: string
  role: 'core' | 'defense' | 'production' | 'mixed' | 'infrastructure'
  buildingCount: number
  defenseCount: number
  productionCount: number
  damagedBuildingCount: number
  enemyUnitCount: number
  priority: number
}

export interface GameAlert {
  kind: 'power-shortage' | 'core-health-low' | 'defense-exposed' | 'buildings-damaged'
  severity: 'warning' | 'critical'
  message: string
  zoneId?: string
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

  const { schemaVersion, snapshotId, gameTick, gameRunning, mapName, wave, players, units, core, power, factories, resources, zones, alerts } = value
  if (
    schemaVersion !== 2
    || typeof snapshotId !== 'string'
    || !isNonNegativeFiniteNumber(gameTick)
    || typeof gameRunning !== 'boolean'
    || (mapName !== null && typeof mapName !== 'string')
    || !isWaveSnapshot(wave)
    || !isPlayerSummary(players)
    || !isUnitSummary(units)
    || !isCoreSnapshot(core)
    || !isPowerSnapshot(power)
    || !isFactoryEntries(factories)
    || !isResourceSummary(resources)
    || !isZoneSummaries(zones)
    || !isGameAlerts(alerts)
  ) throw new TypeError('Mindustry state bridge returned an invalid v2 game snapshot.')

  return { schemaVersion, snapshotId, gameTick, gameRunning, mapName, wave, players, units, core, power, factories, resources, zones, alerts }
}

function isRecord(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }
function isNonNegativeFiniteNumber(value: unknown): value is number { return typeof value === 'number' && Number.isFinite(value) && value >= 0 }
function isString(value: unknown): value is string { return typeof value === 'string' }
function isWaveSnapshot(value: unknown): value is WaveSnapshot {
  return isRecord(value) && isNonNegativeFiniteNumber(value.number) && isNonNegativeFiniteNumber(value.timeRemainingTicks)
}
function isPlayerSummary(value: unknown): value is PlayerSummary { return isRecord(value) && isNonNegativeFiniteNumber(value.count) }
function isUnitSummary(value: unknown): value is UnitSummary {
  return isRecord(value) && isNonNegativeFiniteNumber(value.friendly) && isNonNegativeFiniteNumber(value.enemy)
}
function isCoreSnapshot(value: unknown): value is CoreSnapshot | null {
  return value === null || (isRecord(value) && isNonNegativeFiniteNumber(value.health) && isNonNegativeFiniteNumber(value.maxHealth)
    && value.maxHealth > 0 && Array.isArray(value.inventory) && value.inventory.every(isInventoryEntry))
}
function isInventoryEntry(value: unknown): value is InventoryEntry {
  return isRecord(value) && isString(value.name) && isNonNegativeFiniteNumber(value.amount)
}
function isPowerSnapshot(value: unknown): value is PowerSnapshot {
  return isRecord(value) && isNonNegativeFiniteNumber(value.networkCount) && isNonNegativeFiniteNumber(value.produced)
    && isNonNegativeFiniteNumber(value.needed) && isNonNegativeFiniteNumber(value.stored) && isNonNegativeFiniteNumber(value.capacity)
}
function isFactoryEntries(value: unknown): value is FactoryEntry[] {
  return Array.isArray(value) && value.every(entry => isRecord(entry) && isString(entry.name) && isNonNegativeFiniteNumber(entry.count))
}
function isResourceSummary(value: unknown): value is ResourceSummary {
  return isRecord(value) && Array.isArray(value.critical) && value.critical.every(entry => isRecord(entry)
    && isString(entry.item) && isNonNegativeFiniteNumber(entry.amount) && isNonNegativeFiniteNumber(entry.threshold))
}
function isZoneSummaries(value: unknown): value is ZoneSummary[] {
  const roles = ['core', 'defense', 'production', 'mixed', 'infrastructure']
  return Array.isArray(value) && value.length <= 12 && value.every(zone => isRecord(zone)
    && isString(zone.id) && isString(zone.role) && roles.includes(zone.role)
    && isNonNegativeFiniteNumber(zone.buildingCount) && isNonNegativeFiniteNumber(zone.defenseCount)
    && isNonNegativeFiniteNumber(zone.productionCount) && isNonNegativeFiniteNumber(zone.damagedBuildingCount)
    && isNonNegativeFiniteNumber(zone.enemyUnitCount) && isNonNegativeFiniteNumber(zone.priority))
}
function isGameAlerts(value: unknown): value is GameAlert[] {
  const kinds = ['power-shortage', 'core-health-low', 'defense-exposed', 'buildings-damaged']
  const severities = ['warning', 'critical']
  return Array.isArray(value) && value.every(alert => isRecord(alert) && isString(alert.kind) && kinds.includes(alert.kind)
    && isString(alert.severity) && severities.includes(alert.severity) && isString(alert.message)
    && (alert.zoneId === undefined || isString(alert.zoneId)))
}
