package ru.benos_codex.gofindwin.client.hud

import ru.benos_codex.gofindwin.client.hud.effects.FinishEffectsConfig

data class TimerHudConfig(
    val offsetX: Int = 0,
    val offsetY: Int = -28,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val timerColorBest: Int = 0xFF7CFC00.toInt(),
    val timerColorNormal: Int = 0xFFD0D0D0.toInt(),
    val timerColorWorst: Int = 0xFFFF5555.toInt(),
    val backgroundColor: Int = 0x90000000.toInt(),
    val borderEnabled: Boolean = false,
    val borderThickness: Int = 1,
    val borderColor: Int = 0xFF7F7F7F.toInt(),
    val anchor: TimerHudAnchor = TimerHudAnchor.HOTBAR_CENTER,
    val paddingX: Int = 6,
    val paddingY: Int = 4,
    val fontShadow: Boolean = true,
    val timeFormat: String = "HH:mm:ss:SSS",
    val showTargetItem: Boolean = true,
    val targetItemPosition: TimerHudItemPosition = TimerHudItemPosition.ABOVE,
    val targetItemSlotSize: Int = 22,
    val targetItemGap: Int = 6,
    val finishBlinkEnabled: Boolean = true,
    val finishBlinkDurationMs: Long = 1800L,
    val finishBlinkIntervalMs: Long = 140L,
    val finishEffects: FinishEffectsConfig = FinishEffectsConfig()
)
