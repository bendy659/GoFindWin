package ru.benos.gofindwin.client.data

data class ParticleItemData(
    var itemId: String,
    var itemRenderMode: ItemRenderMode,
    var mColor: String = "#FFFFFF"
) {
    companion object {
        val Example: ParticleItemData = ParticleItemData("minecraft:diamond", ItemRenderMode.D3)
        val ExampleStr: String = "item(\"minecraft:diamond\", #FFFFFF)"
    }
}
