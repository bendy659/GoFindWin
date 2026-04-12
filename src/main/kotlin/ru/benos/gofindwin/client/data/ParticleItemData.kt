package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
data class ParticleItemData(
    var id: String,
    var itemRenderMode: ItemRenderMode,
) {
    companion object {
        val Example: ParticleItemData = ParticleItemData("minecraft:diamond", ItemRenderMode.D3)
    }
}
