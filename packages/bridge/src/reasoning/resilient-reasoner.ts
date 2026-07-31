import { ReasoningDebouncer } from '../memory/reasoning-debouncer.js'
import { AdviceHistory } from './advice-history.js'
import type { ReasoningOutcome, ReasoningRequest, StreamingReasoningClient } from './types.js'

export const DEFAULT_REASONING_TIMEOUT_MS = 3_500

/**
 * Runs asynchronous strategy reasoning outside the mod process. It streams
 * notification text immediately, but never turns generated text into an action.
 */
export class ResilientReasoner {
  constructor(
    private readonly client: StreamingReasoningClient,
    private readonly history: AdviceHistory,
    private readonly debouncer = new ReasoningDebouncer(),
    private readonly timeoutMs = DEFAULT_REASONING_TIMEOUT_MS,
  ) {}

  async reason(request: ReasoningRequest, onText?: (chunk: string) => void): Promise<ReasoningOutcome> {
    if (!this.debouncer.tryAcquire(request.topic))
      return { kind: 'debounced' }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), this.timeoutMs)
    let text = ''
    try {
      for await (const chunk of this.client.stream({ topic: request.topic, prompt: request.prompt, signal: controller.signal })) {
        text += chunk
        onText?.(chunk)
      }
      const notification = {
        id: `notice-${request.snapshot.snapshotId}-${Date.now()}`,
        topic: request.topic,
        text,
        source: request.source,
        refSnapshotId: request.snapshot.snapshotId,
        refTick: request.snapshot.gameTick,
        createdAt: Date.now(),
      }
      this.history.append(notification)
      return { kind: 'completed', notification }
    }
    catch {
      if (controller.signal.aborted)
        return { kind: 'timed-out', fallbackMessage: 'Strategy analysis timed out; local alerts remain authoritative.' }
      return { kind: 'failed', fallbackMessage: 'Strategy analysis is unavailable; local alerts remain authoritative.' }
    }
    finally {
      clearTimeout(timer)
      this.debouncer.release(request.topic)
    }
  }
}
