import type { ActionRequest } from '../action.js'
import type { GameSnapshot } from '../snapshot.js'

const MINIMUM_STARTER_DEFENSE_COPPER = 70
const STARTER_DUO_BLUEPRINT_ID = 'starter-duo-defense'

/** Deterministic, local recommendation rules. No LLM is consulted for this safety-critical candidate. */
export function suggestBlueprintActions(snapshot: GameSnapshot): ActionRequest[] {
  const defenseEvent = snapshot.alerts.find(alert => alert.kind === 'defense-exposed' && alert.zoneId !== undefined)
  const copper = snapshot.core?.inventory.find(item => item.name === 'copper')?.amount ?? 0
  if (defenseEvent?.zoneId === undefined || copper < MINIMUM_STARTER_DEFENSE_COPPER)
    return []

  return [{
    version: 1,
    id: `suggestion-${STARTER_DUO_BLUEPRINT_ID}-${snapshot.snapshotId}`,
    idempotencyKey: `${STARTER_DUO_BLUEPRINT_ID}-${snapshot.snapshotId}`,
    type: 'PLACE_BLUEPRINT',
    refSnapshotId: snapshot.snapshotId,
    refTick: snapshot.gameTick,
    requiresConfirmation: true,
    // The Java mod resolves an actually placeable anchor within this coarse zone.
    target: { zoneId: defenseEvent.zoneId, rotation: 0 },
    payload: { schematicId: STARTER_DUO_BLUEPRINT_ID },
  }]
}
