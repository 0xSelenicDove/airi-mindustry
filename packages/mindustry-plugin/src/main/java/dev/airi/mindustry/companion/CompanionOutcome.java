package dev.airi.mindustry.companion;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable result for the bridge/UI; it describes, rather than performs, native game work. */
public record CompanionOutcome(
    CompanionTaskStatus taskStatus,
    String message,
    Set<String> toolCalls,
    CompanionProposal proposal,
    Optional<CompanionTarget> resolvedTarget,
    CompanionTarget repairTarget,
    int remainingRepairTargets,
    boolean worldMutationAttempted,
    Map<String, Integer> itemsSpent,
    boolean shouldNotifyPlayer,
    CompanionPriority priority,
    boolean companionControlRetained,
    boolean timedOut
) {
    public boolean hasProposal() { return proposal != null; }
    public int itemsSpent(String item) { return itemsSpent.getOrDefault(item, 0); }
}
