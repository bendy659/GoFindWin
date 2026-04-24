package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

@Environment(EnvType.CLIENT)
data class ExpressionBakeData(
    val particleCount   : Int,
    val particlePerMin  : Int,
    val particleLifetime: Int,

    val item   : ParticleItemData?,
    val texture: ParticleTextureData?,
    val block  : ParticleBlockData?,
    val entity : ParticleEntityData?,
    val label  : String?,

    val color: Int,

    val position: Vec2,
    val rotation: Vec3,
    val scale   : Vec3
) {
    companion object {
        class Builder {
            var particleCount   : Int  = 1 // One particle
            var particlePerMin  : Int = 60 // 60 particles in one minute
            var particleLifetime: Int = 20 // 20 tick of life

            var item   : ParticleItemData?    = null
            var texture: ParticleTextureData? = null
            var block  : ParticleBlockData?   = null
            var entity : ParticleEntityData?  = null
            var label  : String? = null

            var color: Int = 
                Color(255, 255, 255, 255).int
             // default white color

            var position: Vec2 = Vec2.ZERO // default position
            var rotation: Vec3 = Vec3.ZERO // default rotation
            var scale   : Vec3 = Vec3.ZERO // default scale

            fun build(): ExpressionBakeData =
                ExpressionBakeData(
                    particleCount,
                    particlePerMin,
                    particleLifetime,
                    item,
                    texture,
                    block,
                    entity,
                    label,
                    color,
                    position,
                    rotation,
                    scale
                )

            fun particleCount(value: Int): Builder {
                particleCount = value

                return this
            }

            fun particlePerMin(value: Int): Builder {
                particlePerMin = value

                return this
            }

            fun particleLifetime(value: Int): Builder {
                particleLifetime = value

                return this
            }

            fun item(value: ParticleItemData): Builder {
                item = value
                texture = null
                block = null
                entity = null
                label = null

                return this
            }

            fun texture(value: ParticleTextureData): Builder {
                item = null
                texture = value
                block = null
                entity = null
                label = null

                return this
            }

            fun block(value: ParticleBlockData): Builder {
                item = null
                texture = null
                block = value
                entity = null
                label = null

                return this
            }

            fun entity(value: ParticleEntityData): Builder {
                item = null
                texture = null
                block = null
                entity = value
                label = null

                return this
            }

            fun label(value: String): Builder {
                item = null
                texture = null
                block = null
                entity = null
                label = value

                return this
            }

            fun color(value: Int): Builder {
                color = value

                return this
            }

            fun position(value: Vec2): Builder {
                position = value

                return this
            }

            fun rotation(value: Vec3): Builder {
                rotation = value

                return this
            }

            fun scale(value: Vec3): Builder {
                scale = value

                return this
            }
        }
    }
}
