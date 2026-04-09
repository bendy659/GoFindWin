package ru.benos.gofindwin.client.hud.effects

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import ru.benos.gofindwin.GoFindWinConst

object ParticleManager {
    private val particles: MutableList<ParticleInstance> = mutableListOf()

    var maxParticles: Int = 4000

    fun spawn(instance: ParticleInstance) {
        particles.addFirst(instance)

        if (particles.size > maxParticles)
            particles.subList(4000, particles.size).clear()
    }

    val init: Unit get() =
        ClientTickEvents.END_CLIENT_TICK.register { _ -> tick }

    val tick: Unit get() {
        val rnd = RandomSource.create()

        particles.removeAll { instance -> instance.age > instance.lifeTime }

        particles.forEach { instance ->
            instance.stPosition(
                instance.startX + rnd.nextInt(-18, 19),
                instance.startY + instance.age * 2 + rnd.nextInt(-1, 2)
            )
            instance.stRotation(instance.age / 2f)
            instance.stScale(
                Mth.lerp(instance.lifeFactor, 0f, 64f),
                Mth.lerp(instance.lifeFactor, 0f, 64f)
            )
            instance.stAlpha(Mth.clamp(1f - instance.lifeFactor, 0f, 1f))

            instance.stColor(
                Mth.lerpInt(
                    instance.lifeFactor,
                    if (instance.color == 0xFFFFFFFF.toInt()) GoFindWinConst.randColor(rnd) else instance.color,
                    0x00000000
                )
            )

            instance.updateAge
        }
    }

    fun draw(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false)

        particles.forEach { instance ->
            if (!instance.drawable) return@forEach

            guiGraphics.pose().pushMatrix()

            val x = Mth.lerp(tickDelta, instance.oldX.toFloat(), instance.x.toFloat())
            val y = Mth.lerp(tickDelta, instance.oldY.toFloat(), instance.y.toFloat())
            val r = Mth.lerp(tickDelta, instance.oldRotation, instance.rotation)
            val w = Mth.lerp(tickDelta, instance.oldWidth, instance.width)
            val h = Mth.lerp(tickDelta, instance.oldHeight, instance.height)
            val c = Mth.lerp(tickDelta, instance.oldColor.toFloat(), instance.color.toFloat()).toInt()
            val a = Mth.lerp(tickDelta, instance.oldAlpha, instance.alpha).coerceIn(0f, 1f)

            guiGraphics.pose().translate(x, y)
            guiGraphics.pose().rotate(r)
            guiGraphics.pose().scale(w, h)

            guiGraphics.fill(-1, -1, 1, 1, ((a * 255f).toInt() shl 24) or (c and 0x00FFFFFF))

            guiGraphics.pose().popMatrix()
        }

        guiGraphics.drawString(
            Minecraft.getInstance().font,
            "Particles: ${particles.size}",
            Minecraft.getInstance().window.guiScaledWidth / 2 - Minecraft.getInstance().font.width("Particles: ${particles.size }") / 2,
            Minecraft.getInstance().window.guiScaledHeight - 64,
            0xFFFFFFFF.toInt(),
            true
        )
    }

    val test: Unit get() {
        val testInstance = mutableListOf<ParticleInstance>()

        val rnd = RandomSource.create()
        val mc = Minecraft.getInstance()

        repeat(4000) {
            testInstance += ParticleInstance(
                x = rnd.nextInt(0, mc.window.guiScaledWidth),
                y = 2,
                rotation = (360 * rnd.nextFloat()) / 180f,

                age = rnd.nextInt(-40, 0),
                lifeTime = 60 + rnd.nextInt(0, 40),

                color = 0xFFFFFFFF.toInt()
            )
        }

        testInstance.forEach(::spawn)
    }
}
