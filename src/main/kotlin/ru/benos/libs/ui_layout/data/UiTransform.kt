package ru.benos.libs.ui_layout.data

import net.minecraft.world.phys.Vec2
import org.joml.Matrix3x2f
import org.joml.Vector2f

data class UiTransform(
    var origin   : Vec2,
    var translate: Vec2,
    var rotation : Float,
    var scale    : Vec2
) {
    companion object {
        val DEFAULT: UiTransform = UiTransform(
            origin    = Vec2(0.5f, 0.5f),
            translate = Vec2.ZERO,
            rotation  = 0f,
            scale     = Vec2.ONE
        )
    }

    fun origin(origin: Vec2): UiTransform {
        this.origin = origin; return this
    }

    fun translate(translate: Vec2): UiTransform {
        this.translate = translate; return this
    }

    fun rotation(rotation: Float): UiTransform {
        this.rotation = rotation; return this
    }

    fun scale(scale: Vec2): UiTransform {
        this.scale = scale; return this
    }

    fun normalizeMouse(mouseX: Float, mouseY: Float, bounds: UiRect): Pair<Float, Float> {
        val pivotX = bounds.x + bounds.width * origin.x
        val pivotY = bounds.y + bounds.height * origin.y

        val transformMatrix = Matrix3x2f()
            .translate(translate.x, translate.y)
            .rotateAbout(rotation, pivotX, pivotY)
            .scaleAround(scale.x, scale.y, pivotX, pivotY)

        val inverseMatrix = Matrix3x2f(transformMatrix).invert()
        val localPoint = inverseMatrix.transformPosition(Vector2f(mouseX, mouseY))

        return localPoint.x to localPoint.y
    }
}
