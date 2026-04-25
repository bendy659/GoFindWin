package ru.benos.libs.ui_layout.data.theme

import net.minecraft.util.ARGB
import ru.benos.libs.ui_layout.data.UiBoxOutlineColor

data class UiBoxTheme(
    val background: Int,
    val outline: UiBoxOutlineColor?,

    var backgroundHovered: Int                = background,
    var outlineHovered   : UiBoxOutlineColor? = outline,

    var backgroundClicked: Int                = background,
    var outlineClicked   : UiBoxOutlineColor? = outline,

    var backgroundReleased: Int                = background,
    var outlineReleased   : UiBoxOutlineColor? = outline,

    var backgroundFocused: Int                = background,
    var outlineFocused   : UiBoxOutlineColor? = outline
) {
    companion object {
        val DEFAULT: UiBoxTheme = UiBoxTheme(
            background = ARGB.color(128, 0, 0, 0),
            outline = UiBoxOutlineColor(1, 128, 255, 255, 255)
        )

        val TRANSPARENT: UiBoxTheme = UiBoxTheme(
            background = ARGB.alpha(0),
            outline = null
        )
    }

    fun hovered(
        background: Int,
        outline: UiBoxOutlineColor? = null
    ): UiBoxTheme {
        backgroundHovered = background
        outlineHovered = outline

        return this
    }

    fun clicked(
        background: Int,
        outline: UiBoxOutlineColor? = null
    ): UiBoxTheme {
        backgroundClicked = background
        outlineClicked = outline

        return this
    }

    fun released(
        background: Int,
        outline: UiBoxOutlineColor? = null
    ): UiBoxTheme {
        backgroundReleased = background
        outlineReleased = outline

        return this
    }

    fun focused(
        background: Int,
        outline: UiBoxOutlineColor? = null
    ): UiBoxTheme {
        backgroundFocused = background
        outlineFocused = outline

        return this
    }
}