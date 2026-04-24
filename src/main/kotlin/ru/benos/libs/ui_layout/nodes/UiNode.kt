package ru.benos.libs.ui_layout.nodes

import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize

interface UiNode {
    val modifier: UiModifier

    fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize
    fun render(runtime: UiRuntime, bounds: UiRect)
}