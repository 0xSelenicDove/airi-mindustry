/**
 * Every write-capable proposal must be tied to the snapshot the agent used.
 * This is a contract only: the current bridge deliberately exposes no action endpoint.
 */
export interface ActionRequest {
  version: 1
  id: string
  /** Deduplicates retries of a proposed world-changing operation. */
  idempotencyKey: string
  type: 'NOTIFY_USER' | 'OPEN_BLUEPRINT_PREVIEW' | 'PLACE_BLUEPRINT' | 'PAUSE_OPTIONAL_POWER_LOADS' | 'COMMAND_REPAIR_UNITS'
  refSnapshotId: string
  refTick: number
  requiresConfirmation: boolean
  /** Zone is authoritative for placement; the mod resolves a valid local anchor. */
  target?: { zoneId: string, x?: number, y?: number, rotation?: number }
  payload: Record<string, unknown>
}

export interface ActionResult {
  id: string
  accepted: boolean
  status: 'ACCEPTED' | 'INVALID_ACTION_REFERENCE' | 'STALE_WORLD_STATE' | 'INSUFFICIENT_ITEMS' | 'INVALID_PLACEMENT' | 'CONFIRMATION_REQUIRED'
  message?: string
}

/** Text is historical advice; only world-changing actions inherit tick expiry. */
export function requiresFreshSnapshot(action: Pick<ActionRequest, 'type'>): boolean {
  return action.type === 'PLACE_BLUEPRINT'
    || action.type === 'PAUSE_OPTIONAL_POWER_LOADS'
    || action.type === 'COMMAND_REPAIR_UNITS'
}
