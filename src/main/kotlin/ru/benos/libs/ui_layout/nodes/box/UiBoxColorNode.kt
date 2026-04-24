package ru.benos.libs.ui_layout.nodes.box

import net.minecraft.util.ARGB
import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiBoxOutlineColor
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.nodes.UiNode

@UiDsl
class UiBoxColorNode(
    private val backgroundColor: Int,
    private val outline        : UiBoxOutlineColor?,

    enableScissor: Boolean,
    children     : List<UiNode>,

    override val modifier: UiModifier
) : UiBoxNode(enableScissor, children, modifier) {
    companion object {
        fun UiBuilder.boxColor(
            backgroundColor: Int = ARGB.alpha(0),
            outline: UiBoxOutlineColor? = null,
            enableScissor: Boolean = false,
            modifier: UiModifier = UiModifier,
            block: UiBuilder.() -> Unit
        ) {
            val children = UiBuilder().apply(block).build()
            val node = UiBoxColorNode(backgroundColor, outline, enableScissor, children, modifier)

            this@boxColor.addNode(node)
        }
    }

    override fun renderBackground(runtime: UiRuntime, bounds: UiRect) {
        // Background //
        runtime.guiGraphics.fill(
            bounds.x, bounds.y,
            bounds.right, bounds.bottom,
            backgroundColor
        )

        // Outline //
        if (outline != null && outline.width > 0) {
            runtime.guiGraphics.fill( // top //
                bounds.x, bounds.y,
                bounds.right, bounds.y + outline.width,
                outline.color
            )
            runtime.guiGraphics.fill( // bottom //
                bounds.x, bounds.bottom - outline.width,
                bounds.right, bounds.bottom,
                outline.color
            )
            runtime.guiGraphics.fill( // left //
                bounds.x, bounds.y + outline.width,
                bounds.x + outline.width, bounds.bottom - outline.width,
                outline.color
            )
            runtime.guiGraphics.fill( // right //
                bounds.right - outline.width, bounds.y + outline.width,
                bounds.right, bounds.bottom - outline.width,
                outline.color
            )
        }
    }
}