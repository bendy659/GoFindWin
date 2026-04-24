package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
data class ParticleItemData(
    var id: String = "minecraft:diamond",
    var particleRenderMode: ParticleRenderMode? = ParticleRenderMode.VOLUME,
) {
    val resolvedRenderMode: ParticleRenderMode
        get() = particleRenderMode ?: ParticleRenderMode.VOLUME

    companion object {
        val Example: ParticleItemData = ParticleItemData(
            id = "minecraft:diamond",
            particleRenderMode = ParticleRenderMode.VOLUME
        )
    }

    override fun toString(): String =
        "item($id, ${resolvedRenderMode.id})"
}
