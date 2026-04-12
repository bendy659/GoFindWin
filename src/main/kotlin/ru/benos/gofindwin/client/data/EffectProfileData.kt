package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
data class EffectProfileData(
    val profileName: String,

    // General //
    val emitMode: ParticleEmitMode,
    val lifetime: String,
    val count: String,

    // End === //

    // Visual //
    val type   : ParticleDisplayType,
    val item   : ParticleItemData?    = null,
    val texture: ParticleTextureData? = null,
    val label  : String?              = null,

    val color  : Int,
    // End == //

    // Motion //
    val position: String,
    val rotation: String,
    val scale   : String,
    // End == //

    // Baking //
    val iterations: Int
    // End == //
)