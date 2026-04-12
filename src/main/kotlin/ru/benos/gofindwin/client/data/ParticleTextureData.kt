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
        val Example: ParticleTextureData = ParticleTextureData("minecraft:particle/flash", 0f, 0f, 16f, 16f)
    }
}