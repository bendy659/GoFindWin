package ru.benos_codex.gofindwin.client.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents
import ru.benos_codex.gofindwin.client.hud.effects.FinishEffectCategory
import ru.benos_codex.gofindwin.client.hud.effects.HudCelebrationEffectsClient
import ru.benos_codex.gofindwin.hud.RunTimerHudState
import ru.benos_codex.gofindwin.run.RunPhase

object RunFinishEffectsClient {
    fun handleStateChange(previous: RunTimerHudState, next: RunTimerHudState) {
        if (previous.phase == next.phase) return
        if (previous.phase != RunPhase.RUNNING) return
        if (next.phase !in setOf(RunPhase.FINISHING, RunPhase.POST_FINISH)) return

        val soundManager = Minecraft.getInstance().soundManager
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BELL_RESONATE, 1.0f, 0.95f))
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.9f, 1.1f))
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f))
        HudCelebrationEffectsClient.requestBurst(FinishEffectCategory.AVERAGE)
    }
}
