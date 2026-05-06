package ru.benos.libs.ui_layout.nodes

import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.UiTransform

@UiDsl
abstract class UiNode {
    abstract val modifier: UiModifier

    abstract fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize

    open fun render(runtime: UiRuntime, bounds: UiRect) {
        registerEvents(runtime, bounds)
    }

    protected fun registerEvents(runtime: UiRuntime, bounds: UiRect) {
        modifier.onMouseClicked?.let { runtime.addMouseClicked(bounds, modifier.transform, it) }

        if (modifier.onMouseEnter != null || modifier.onMouseExit != null || modifier.onMouseHovered != null) {
            val (localX, localY) = modifier.transform
                .normalizeMouse(runtime.mouseX.toFloat(), runtime.mouseY.toFloat(), bounds)

            val isHovered = runtime.trackHover(bounds, localX, localY)
            val wasHovered = runtime.isHovered(bounds)

            if (isHovered && !wasHovered) modifier.onMouseEnter?.invoke()
            if (!isHovered && wasHovered) modifier.onMouseExit?.invoke()
            if (isHovered) modifier.onMouseHovered?.invoke(localX.toInt(), localY.toInt())
        }

        modifier.onMouseReleased?.let { runtime.addMouseReleased(bounds, modifier.transform, it) }
    }
}
