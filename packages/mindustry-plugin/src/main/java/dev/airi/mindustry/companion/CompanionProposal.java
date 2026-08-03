package dev.airi.mindustry.companion;

/** A proposal is inert until player confirmation and fresh local validation. */
public record CompanionProposal(String type, String schematicId, boolean requiresConfirmation) {}
