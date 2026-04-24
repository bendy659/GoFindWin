package ru.benos.gofindwin.client.particle

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphics
import ru.benos.gofindwin.client.data.ExpressionBakeData
import ru.benos_codex.client.particle.ParticlePlayback
import ru.benos_codex.client.particle.ParticleRenderer

@Environment(EnvType.CLIENT)
class ParticlePlayer(
    bake: List<List<ExpressionBakeData?>> = emptyList()
) {
    private val playback = ParticlePlayback()

    var currentBake: List<List<ExpressionBakeData?>> = emptyList()
        private set

    var totalTicks: Int = -1
        private set
    var currentTick: Int = 0
        private set

    val displayTotalTicks: Int
        get() = currentBake.size

    val displayCurrentTick: Int
        get() = if (currentBake.isEmpty()) 0 else currentTick + 1

    val currentParticleCount: Int
        get() = currentBake
            .getOrNull(currentTick)
            ?.count { it != null }
            ?: 0

    var ticking: Boolean = false
    var looping: Boolean = false

    init {
        setBake(bake)
    }

    fun setBake(bake: List<List<ExpressionBakeData?>>) {
        currentBake = bake
        currentTick = 0
        totalTicks = currentBake.lastIndex
        ticking = false
        playback.reset()

        if (currentBake.isNotEmpty()) {
            playback.sync(currentBake.first())
        }
    }

    fun tick() {
        if (!ticking) return
        if (currentBake.isEmpty()) return
        if (currentTick >= currentBake.lastIndex) {
            if (looping) play()
            else ticking = false
            return
        }

        currentTick += 1
        playback.sync(currentBake[currentTick])
    }

    fun play() {
        if (currentBake.isEmpty()) return

        currentTick = 0
        ticking = true
        playback.reset()
        playback.sync(currentBake.first())
    }

    fun resume() {
        if (currentBake.isEmpty()) return
        if (playback.particles.isEmpty()) {
            play(); return
        }

        ticking = true
    }

    fun stop() {
        ticking = false
        currentTick = 0
        playback.reset()
    }

    fun draw(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, drawX: Float, drawY: Float) {
        if (currentBake.isEmpty()) return
        if (currentTick !in currentBake.indices) return
        if (playback.particles.isEmpty()) return

        ParticleRenderer.draw(
            guiGraphics = guiGraphics,
            particles = playback.particles,
            drawX = drawX,
            drawY = drawY,
            partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
        )
    }
}
