import type { GameSnapshot } from '../snapshot.js'
import type { ResilientReasoner } from './resilient-reasoner.js'

const PREFETCH_WAVE_WINDOW_TICKS = 20 * 60
const PREFETCH_BATTERY_PERCENT = 35

export interface PrefetchTopic {
  topic: 'next-wave-preparation' | 'battery-reserve'
  reason: string
}

/** Identifies early-warning conditions without invoking an LLM on the game path. */
export function getPrefetchTopics(snapshot: GameSnapshot): PrefetchTopic[] {
  if (!snapshot.gameRunning)
    return []

  const topics: PrefetchTopic[] = []
  if (snapshot.wave.timeRemainingTicks > 0 && snapshot.wave.timeRemainingTicks < PREFETCH_WAVE_WINDOW_TICKS)
    topics.push({ topic: 'next-wave-preparation', reason: 'The next wave is less than 20 seconds away.' })

  if (snapshot.power.capacity > 0 && (snapshot.power.stored / snapshot.power.capacity) * 100 < PREFETCH_BATTERY_PERCENT)
    topics.push({ topic: 'battery-reserve', reason: 'Battery reserve is below 35%.' })
  return topics
}

/**
 * Starts eligible strategy work without awaiting it. The caller can run this
 * after a bridge snapshot arrives; the mod never waits for this background work.
 */
export function prefetchStrategy(snapshot: GameSnapshot, reasoner: ResilientReasoner): void {
  for (const prefetch of getPrefetchTopics(snapshot)) {
    void reasoner.reason({
      topic: prefetch.topic,
      prompt: prefetch.reason,
      snapshot: { snapshotId: snapshot.snapshotId, gameTick: snapshot.gameTick },
      source: 'trend-prefetch',
    })
  }
}
