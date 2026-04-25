package ru.benos.libs.ui_layout.data

import net.minecraft.util.ARGB

data class UiBoxOutlineColor(
    val width: Int,
    val color: Int = 0xFFFFFFFF.toInt()
) {
    // RGBA [Int] //
    constructor(
        width: Int = 1,
        a: Int = 255, r: Int, g: Int, b: Int
    ): this(width, ARGB.color(a, r, g, b))

    // RGBA [Float] //
    constructor(
        width: Int = 1,
        a: Float = 1.0f, r: Float, g: Float, b: Float
    ): this(width, ARGB.colorFromFloat(a, r, g, b))
}