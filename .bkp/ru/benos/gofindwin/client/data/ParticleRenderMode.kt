package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
enum class ParticleRenderMode(val id: String) {
    FLAT("flat"), VOLUME("volume")
}