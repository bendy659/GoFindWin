package ru.benos.gofindwin.client.data

data class ParticleBlockData(
    val id: String,
    val particleRenderMode: ParticleRenderMode
) {
    companion object {
        val Example: ParticleBlockData = ParticleBlockData(
            id = "minecraft:gras_block",
            particleRenderMode = ParticleRenderMode.FLAT
        )
    }
}