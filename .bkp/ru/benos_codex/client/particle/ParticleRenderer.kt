package ru.benos_codex.client.particle

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import kotlin.math.roundToInt
import ru.benos.gofindwin.GoFindWinConst.ident
import ru.benos.gofindwin.client.data.ParticleInstance

@Environment(EnvType.CLIENT)
object ParticleRenderer {
    private val DIAMOND_TEXTURE: Identifier = "minecraft:textures/item/diamond.png".ident
    private const val DEFAULT_SIZE = 16f

    fun draw(
        guiGraphics: GuiGraphics,
        particles: List<ParticleInstance>,
        drawX: Float,
        drawY: Float,
        partialTick: Float
    ) {
        particles.forEach { particle ->
            val x = lerp(particle.oldPosition.x, particle.position.x, partialTick) + drawX
            val y = lerp(particle.oldPosition.y, particle.position.y, partialTick) + drawY
            val size = resolveSize(particle, partialTick)

            val left = (x - size / 2f).roundToInt()
            val top = (y - size / 2f).roundToInt()
            val right = left + size.roundToInt()
            val bottom = top + size.roundToInt()

            guiGraphics.blit(DIAMOND_TEXTURE, left, top, right, bottom, 0f, 1f, 0f, 1f)
        }
    }

    private fun resolveSize(particle: ParticleInstance, partialTick: Float): Float {
        val width = lerp(particle.oldScale.x, particle.scale.x, partialTick)
        val height = lerp(particle.oldScale.y, particle.scale.y, partialTick)
        val size = maxOf(width, height)

        return if (size > 0.01f) size else DEFAULT_SIZE
    }

    private fun lerp(start: Float, end: Float, partialTick: Float): Float =
        Mth.lerp(partialTick, start, end)
}
