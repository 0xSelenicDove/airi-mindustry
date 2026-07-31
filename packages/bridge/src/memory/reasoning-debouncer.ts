/**
 * Local short-term memory for asynchronous reasoning. A caller acquires a
 * topic before starting an LLM request and releases it when that request ends;
 * duplicate alerts therefore cannot create concurrent requests for the same
 * underlying problem.
 */
export class ReasoningDebouncer {
  private readonly inFlight = new Map<string, number>()

  /** Returns false when a request for this normalized topic is already running. */
  tryAcquire(topic: string): boolean {
    const key = normalizeTopic(topic)
    if (this.inFlight.has(key))
      return false

    this.inFlight.set(key, Date.now())
    return true
  }

  release(topic: string): void {
    this.inFlight.delete(normalizeTopic(topic))
  }

  /** Clears abandoned locks after a bounded timeout so an LLM failure cannot deadlock advice. */
  expireOlderThan(maxAgeMs: number, now = Date.now()): void {
    for (const [topic, startedAt] of this.inFlight) {
      if (now - startedAt > maxAgeMs)
        this.inFlight.delete(topic)
    }
  }
}

function normalizeTopic(topic: string): string {
  return topic.trim().toLowerCase().replace(/\s+/g, ' ')
}
