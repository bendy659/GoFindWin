package ru.benos_codex.gofindwin.client.hud

import ru.benos_codex.gofindwin.hud.RunTimerHudState

object RunTimerHudStateStore {
    @Volatile
    var state: RunTimerHudState = RunTimerHudState.HIDDEN
        private set

    @Volatile
    private var lastFinishStartedAtMs: Long = 0L

    fun update(newState: RunTimerHudState) {
        val previous = state
        state = newState

        if (previous.phase != newState.phase && newState.phase == ru.benos_codex.gofindwin.run.RunPhase.FINISHING) {
            lastFinishStartedAtMs = System.currentTimeMillis()
        }

        RunFinishEffectsClient.handleStateChange(previous, newState)
    }

    fun clear() {
        state = RunTimerHudState.HIDDEN
        lastFinishStartedAtMs = 0L
    }

    fun finishBlinkStartedAtMs(): Long = lastFinishStartedAtMs

    fun isFinishBlinkActive(durationMs: Long): Boolean {
        if (lastFinishStartedAtMs <= 0L) return false
        return System.currentTimeMillis() - lastFinishStartedAtMs <= durationMs
    }
}
