import type { GameSnapshot } from '../snapshot.js'

/** Provider-neutral streaming contract; AIRI/provider adapters implement this. */
export interface StreamingReasoningClient {
  stream(request: { topic: string, prompt: string, signal: AbortSignal }): AsyncIterable<string>
}

export interface ReasoningRequest {
  topic: string
  prompt: string
  snapshot: Pick<GameSnapshot, 'snapshotId' | 'gameTick'>
  source: 'player-request' | 'trend-prefetch'
}

export interface NotificationHistoryEntry {
  id: string
  topic: string
  text: string
  source: ReasoningRequest['source']
  refSnapshotId: string
  refTick: number
  createdAt: number
}

export type ReasoningOutcome =
  | { kind: 'completed', notification: NotificationHistoryEntry }
  | { kind: 'timed-out', fallbackMessage: string }
  | { kind: 'failed', fallbackMessage: string }
  | { kind: 'debounced' }
