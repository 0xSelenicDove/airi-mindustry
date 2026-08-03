const MAX_ACTION_AGE_TICKS = 180

const copy = value => structuredClone(value)

/** Keeps player-reviewed actions immutable, one-time, and tick-bounded. */
export class ProposalStore {
  #nextId = 1
  #pending = new Map()
  #confirmed = new Set()

  prepare(action, preparedAtTick) {
    if (!action || typeof action !== 'object') throw new TypeError('A structured action proposal is required.')
    if (!Number.isSafeInteger(preparedAtTick) || preparedAtTick < 0) throw new TypeError('A valid game tick is required.')
    const id = `mindustry-proposal-${this.#nextId++}`
    this.#pending.set(id, { action: copy(action), preparedAtTick })
    return { id, preparedAtTick, ...copy(action) }
  }

  get(id) {
    const proposal = this.#pending.get(id)
    return proposal ? copy(proposal.action) : undefined
  }

  confirm(id, currentTick) {
    if (this.#confirmed.has(id)) throw new Error('Proposal already confirmed.')
    const proposal = this.#pending.get(id)
    if (!proposal) throw new Error('Unknown proposal ID.')
    if (!Number.isSafeInteger(currentTick) || currentTick < 0) throw new TypeError('A valid game tick is required.')
    if (currentTick - proposal.action.refTick > MAX_ACTION_AGE_TICKS)
      throw new Error('Proposal is stale and must be prepared again from fresh game state.')
    this.#pending.delete(id)
    this.#confirmed.add(id)
    return copy(proposal.action)
  }

  cancel(id) {
    if (!this.#pending.has(id)) throw new Error('Unknown proposal ID.')
    this.#pending.delete(id)
  }
}

export { MAX_ACTION_AGE_TICKS }
