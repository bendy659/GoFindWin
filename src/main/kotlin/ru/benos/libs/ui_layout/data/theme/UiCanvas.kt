package ru.benos.libs.ui_layout.data.theme

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import ru.benos.libs.ui_layout.data.UiRect

sealed interface UiCanvas {
    val hex: Int
    val outline: Int

    fun render(guiGraphics: GuiGraphics, bound: UiRect)

    data class Color(
        override val hex: Int,
        override val outline: Int
    ) : UiCanvas {
        constructor(a: Int, r: Int, g: Int, b: Int, outline: Int = 0)         : this(ARGB.color(a, r, g, b), outline)
        constructor(a: Float, r: Float, g: Float, b: Float, outline: Int = 0) : this(ARGB.colorFromFloat(a, r, g ,b), outline)

        override fun render(guiGraphics: GuiGraphics, bound: UiRect) {
            when (outline > 0) {
                false  -> guiGraphics.fill(bound.x, bound.y, bound.right, bound.bottom, hex)
                true ->  {
                    guiGraphics.fill(bound.x, bound.y, bound.right, bound.y + outline, hex) // top
                    guiGraphics.fill(bound.x, bound.bottom - outline, bound.right, bound.bottom, hex) // bottom
                    guiGraphics.fill(bound.x, bound.y + outline, bound.x + outline, bound.bottom - outline, hex) // left
                    guiGraphics.fill(bound.right - outline, bound.y + outline, bound.right, bound.bottom - outline, hex) // right
                }
            }
        }
    }

    data class Texture(
        val id : Identifier,
        override val hex: Int = 0xFFFFFFFF.toInt(),
        override val outline: Int = 0
    ) : UiCanvas {
        override fun render(guiGraphics: GuiGraphics, bound: UiRect) {
            when (outline > 0) {
                false -> guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    id,
                    bound.x, bound.y,
                    0f, 0f,
                    bound.width, bound.height,
                    bound.width, bound.height,
                    hex
                )
                true -> TODO()
            }
        }
    }
}
