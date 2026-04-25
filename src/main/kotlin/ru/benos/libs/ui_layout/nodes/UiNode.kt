package ru.benos.libs.ui_layout.nodes

import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize

abstract class UiNode {
    abstract val modifier: UiModifier

    abstract fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize

    open fun render(runtime: UiRuntime, bounds: UiRect) {
        registerEvents(runtime, bounds)
    }

    private fun registerEvents(runtime: UiRuntime, bounds: UiRect) {
        modifier.onMouseClicked?.let { runtime.addMouseClicked(bounds, it) }

        if (modifier.onMouseEnter != null || modifier.onMouseExit != null || modifier.onMouseHovered != null) {
            val isHovered = runtime.trackHover(bounds)
            val wasHovered = runtime.isHovered(bounds)

            if (isHovered && !wasHovered)
                modifier.onMouseEnter?.invoke()

            if (!isHovered && wasHovered)
                modifier.onMouseExit?.invoke()

            if (isHovered)
                modifier.onMouseHovered?.invoke(runtime.mouseX, runtime.mouseY)
        }

        modifier.onMouseReleased?.let { runtime.addMouseReleased(bounds, it) }
    }
}