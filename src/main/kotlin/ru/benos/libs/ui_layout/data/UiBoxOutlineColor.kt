package ru.benos.libs.ui_layout.data

import net.minecraft.util.ARGB

data class UiBoxOutlineColor(
    val width: Int,
    val color: Int
) {
    companion object {
        fun uiBoxOutlineColor(width: Int = 1, color: Int): UiBoxOutlineColor =
            UiBoxOutlineColor(width, color)

        fun uiBoxOutlineColor(width: Int = 1, r: Int, g: Int, b: Int, a: Int = 255): UiBoxOutlineColor =
            uiBoxOutlineColor(width, ARGB.color(a, r, g, b))

        fun uiBoxOutlineColor(width: Int = 1, r: Float, g: Float, b: Float, a: Float = 1.0f): UiBoxOutlineColor =
            uiBoxOutlineColor(width, ARGB.colorFromFloat(a, r, g, b))
    }
}