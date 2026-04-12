package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
enum class ParticleEmitMode(val id: String) {
    SINGLE("single"),
    CONTINUOUS("continuous")
}