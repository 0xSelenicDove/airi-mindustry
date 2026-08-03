package dev.airi.mindustry.companion;

/** A confirmed or advisory companion request plus the current adapter observation. */
public record CompanionRequest(CompanionCommand command, CompanionObservation observation, boolean confirmed) {}
