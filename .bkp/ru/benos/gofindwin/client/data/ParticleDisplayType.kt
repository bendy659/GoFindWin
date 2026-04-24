package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
enum class ParticleDisplayType(val id: String) {
    ITEM("item"),
    TEXTURE("texture"),
    LABEL("label")
}