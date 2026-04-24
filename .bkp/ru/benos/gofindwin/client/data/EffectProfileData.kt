package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClientTranslate.mtranslate

@Environment(EnvType.CLIENT)
data class EffectProfileData(
    var profileName: String,

    // General //
    var emitMode: ParticleEmitMode,
    var lifetime: String,
    var count: String,

    // End === //

    // Visual //
    var type   : ParticleDisplayType,
    var item   : ParticleItemData?    = null,
    var texture: ParticleTextureData? = null,
    var label  : String?              = null,

    var color  : String,
    // End == //

    // Motion //
    var position: String,
    var rotation: String,
    var scale   : String,
    // End == //

    // Baking //
    var bakeIterations: Int
    // End == //
) {
    companion object {
        val DEFAULT: EffectProfileData = EffectProfileData(
            "$MOD_ID.profiles.name.default".mtranslate.string,

            emitMode = ParticleEmitMode.SINGLE,
            lifetime = "60",
            count = "32",

            type = ParticleDisplayType.ITEM,
            item = ParticleItemData.Example,
            color = "color(1.0, 1.0, 1.0)",

            position = "vec2(0.0, 0.0)",
            rotation = "vec3(0.0, 0.0, 0.0)",
            scale = "vec3(0.0, 0.0, 0.0)",

            bakeIterations = 1
        )
    }
}