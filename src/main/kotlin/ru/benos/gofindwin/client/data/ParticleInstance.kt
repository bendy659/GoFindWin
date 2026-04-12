package ru.benos.gofindwin.client.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec2 as McVec2
import net.minecraft.world.phys.Vec3 as McVec3

@Environment(EnvType.CLIENT)
data class Vec2(var x: Float, var y: Float) {
    val toMcVec2: McVec2 = McVec2(x, y)
}

@Environment(EnvType.CLIENT)
data class Vec3(var x: Float, var y: Float, var z: Float) {
    val toMcVec3: McVec3 = McVec3(x.toDouble(), y.toDouble(), z.toDouble())
}

@Environment(EnvType.CLIENT)
data class Color(var r: Int, var g: Int, var b: Int, var a: Int) {
    val int: Int get() =
        ARGB.color(a, r, g, b)
}

@Environment(EnvType.CLIENT)
data class ParticleInstance(
    var index: Int,
    var lifetime: Int,
    var age: Int = 0,
    var spawnPosition: Vec2? = null,

    var item   : ParticleItemData? = null,
    var texture: ParticleTextureData? = null,
    var label  : String? = null,
    var color  : Color   = Color(255, 255, 255, 255),

    var position: Vec2 = spawnPosition ?: Vec2(0.0f, 0.0f),
    var rotation: Vec3 = Vec3(0.0f, 0.0f, 0.0f),
    var scale   : Vec3 = Vec3(1.0f, 1.0f, 1.0f),

    var oldPosition: Vec2   = spawnPosition ?: position,
    var oldRotation: Vec3   = rotation,
    var oldScale   : Vec3   = scale,
    var oldColor   : Color = color
) {
    val lifeFactor: Float get() = age.toFloat() / lifetime.toFloat()

    fun updatePosition(x: Number, y: Number) {
        if (spawnPosition == null)
            spawnPosition = position

        oldPosition = position
        position.x = x.toFloat(); position.y = y.toFloat()
    }

    fun updateRotation(x: Number, y: Number, z: Number) {
        oldRotation = rotation
        rotation.x = x.toFloat(); rotation.y = y.toFloat(); rotation.z = z.toFloat()
    }

    fun updateScale(x: Number, y: Number, z: Number) {
        oldScale = scale
        scale.x = x.toFloat(); scale.y = y.toFloat(); scale.z = z.toFloat()
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        oldColor = color
        color.a = a; color.g = g; color.b = b; color.a = a
    }
}