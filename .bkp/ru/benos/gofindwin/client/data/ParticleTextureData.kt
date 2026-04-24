package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
data class ParticleTextureData(
    var id: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float
) {
    companion object {
        val Example: ParticleTextureData = ParticleTextureData(
            id = "minecraft:particle/flash",
            x = 0f, y= 0f,
            w = 16f, h= 16f
        )
    }
}