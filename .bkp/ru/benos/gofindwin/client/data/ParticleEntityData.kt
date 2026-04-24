package ru.benos.gofindwin.client.data

data class ParticleEntityData(
    val id: String,
    val particleRenderMode: ParticleRenderMode
) {
    companion object {
        val Example: ParticleEntityData = ParticleEntityData(
            id = "minecraft:creeper",
            particleRenderMode = ParticleRenderMode.FLAT
        )
    }
}
