package ru.benos.libs.ui_layout.builder

import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.nodes.UiNode

@UiDsl
class UiBuilder {
    private val children: MutableList<UiNode> = mutableListOf()

    fun addNode(node: UiNode) =
        children.add(node)

    fun build(): List<UiNode> =
        children.toList()
}