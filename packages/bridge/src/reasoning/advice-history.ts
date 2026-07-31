import type { NotificationHistoryEntry } from './types.js'

/**
 * Informational advice is independent of world-state freshness: it remains
 * useful as a history record even after the referenced snapshot has aged out.
 */
export class AdviceHistory {
  private readonly entries: NotificationHistoryEntry[] = []

  append(entry: NotificationHistoryEntry): void {
    this.entries.push(entry)
  }

  list(): readonly NotificationHistoryEntry[] {
    return this.entries
  }
}
