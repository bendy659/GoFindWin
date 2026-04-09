package ru.benos.gofindwin.client.data

data class ParticleTextureData(
    var textureId: String,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
    var mColor: String = "#FFFFFF"
) {
    companion object {
        val Example: ParticleTextureData = ParticleTextureData("minecraft:particle/flash", 0f, 0f, 16f, 16f)
        val ExampleStr: String = "texture(\"minecraft:particle/flash\", 0.0, 0.0, 16.0, 16.0, #FFFFFF)"
    }
}