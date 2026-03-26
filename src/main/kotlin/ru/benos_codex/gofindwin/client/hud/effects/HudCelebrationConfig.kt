package ru.benos_codex.gofindwin.client.hud.effects

data class HudCelebrationConfig(
    val enabled: Boolean = true,
    val emitters: List<HudCelebrationEmitterConfig> = listOf(HudCelebrationEmitterConfig())
)
