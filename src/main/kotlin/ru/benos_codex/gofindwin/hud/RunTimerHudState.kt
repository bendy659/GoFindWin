package ru.benos_codex.gofindwin.hud

import ru.benos_codex.gofindwin.run.RunPhase

data class RunTimerHudState(
    val phase: RunPhase,
    val visible: Boolean,
    val elapsedMs: Long,
    val running: Boolean,
    val targetItemId: String?
) {
    companion object {
        val HIDDEN = RunTimerHudState(
            phase = RunPhase.IDLE,
            visible = false,
            elapsedMs = 0L,
            running = false,
            targetItemId = null
        )
    }
}
