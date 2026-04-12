package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.benos.gofindwin.GoFindWinConst.Client.literal

@Environment(EnvType.CLIENT)
data class ExpressionBakeData(
    val particleCount: List<Int>,
    val particlePerMin: List<Int>,
    val particleLifetime: List<Int>,

    val item: List<ParticleItemData>,
    val texture: List<ParticleTextureData>,
    val label: List<Component>,

    val color: List<Int>,

    val position: List<Vec2>,
    val rotation: List<Vec3>,
    val scale   : List<Vec3>
) {
    companion object {
        class Builder {
            var particleCount   : List<Int>  = listOf(1) // One particle
            var particlePerMin  : List<Int> = listOf(60) // 60 particles in one minute
            var particleLifetime: List<Int> = listOf(20) // 20 tick of life

            var item   : List<ParticleItemData> = listOf(ParticleItemData.Example) // default item flat stack
            var texture: List<ParticleTextureData> = listOf(ParticleTextureData.Example)
            var label  : List<Component> = listOf("GoFindWin".literal)

            var color: List<Int> = listOf(
                Color(255, 255, 255, 255).int
            ) // default white color

            var position: List<Vec2> = listOf(Vec2.ZERO) // default position
            var rotation: List<Vec3> = listOf(Vec3.ZERO) // default rotation
            var scale   : List<Vec3> = listOf(Vec3.ZERO) // default scale

            fun build(): ExpressionBakeData =
                ExpressionBakeData(
                    particleCount,
                    particlePerMin,
                    particleLifetime,
                    item,
                    texture,
                    label,
                    color,
                    position,
                    rotation,
                    scale
                )

            fun particleCount(vararg value: Int): Builder {
                particleCount = value.toList()
                return this
            }

            fun particlePerMin(vararg value: Int): Builder {
                particlePerMin = value.toList()
                return this
            }

            fun particleLifetime(vararg value: Int): Builder {
                particleLifetime = value.toList()
                return this
            }

            fun item(vararg value: ParticleItemData): Builder {
                item = value.toList()
                return this
            }

            fun texture(vararg value: ParticleTextureData): Builder {
                texture = value.toList()
                return this
            }

            fun label(vararg value: Component): Builder {
                label = value.toList()
                return this
            }

            fun color(vararg value: Int): Builder {
                color = value.toList()
                return this
            }

            fun position(vararg value: Vec2): Builder {
                position = value.toList()
                return this
            }

            fun rotation(vararg value: Vec3): Builder {
                rotation = value.toList()
                return this
            }

            fun scale(vararg value: Vec3): Builder {
                scale = value.toList()
                return this
            }
        }
    }
}