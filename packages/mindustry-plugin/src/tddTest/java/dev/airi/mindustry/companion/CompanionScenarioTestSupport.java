package dev.airi.mindustry.companion;

/** Shared red-test entry point for the future deterministic companion controller. */
abstract class CompanionScenarioTestSupport {
    private final CompanionUxController controller = CompanionUxController.forTddAcceptanceTests();

    protected final CompanionUxController controller() {
        return controller;
    }
}
