package ru.benos.libs.ui_layout.nodes.grid

import ru.benos.libs.ui_layout.UiDsl
import ru.benos.libs.ui_layout.UiRuntime
import ru.benos.libs.ui_layout.data.UiLength
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.axis.UiAxis
import ru.benos.libs.ui_layout.data.theme.UiBoxColorTheme
import ru.benos.libs.ui_layout.nodes.UiBoxNode
import ru.benos.libs.ui_layout.nodes.UiNode

@UiDsl
class UiLinearLayoutNode(
    private val axis: UiAxis,
    boxTheme: UiBoxColorTheme,
    enableScissor: Boolean,
    private val gap: Int,
    private val children: List<UiNode>,

    override val modifier: UiModifier
) : UiBoxNode(boxTheme, enableScissor, children, modifier) {
    override fun measure(runtime: UiRuntime, maxWidth: Int, maxHeight: Int): UiSize {
        val inner = UiRect(0, 0, maxWidth, maxHeight).shrink(modifier.padding)
        val measured = children.map { it.measure(runtime, inner.width, inner.height) }

        val contentWidth : Int
        val contentHeight: Int
        val measureGap = gap * (children.size - 1)

        when (axis) {
            UiAxis.Horizontal -> {
                contentWidth  = measured.sumOf(UiSize::width) + measureGap
                    .coerceAtLeast(0)
                contentHeight = measured.maxOfOrNull(UiSize::height) ?: 0
            }
            UiAxis.Vertical   -> {
                contentWidth  = measured.maxOfOrNull(UiSize::width) ?: 0
                contentHeight = measured.sumOf(UiSize::height) + measureGap
                    .coerceAtLeast(0)
            }
        }

        return UiSize(
            modifier.resolveWidth(contentWidth, maxWidth),
            modifier.resolveHeight(contentHeight, maxHeight)
        )
    }

    override fun renderChildren(runtime: UiRuntime, inner: UiRect) {
        val measured = children.map { it.measure(runtime, inner.width, inner.height) }

        val mainAxis: Int = axiz(inner.width, inner.height)
        val fixedSize = measured.zip(children).sumOf { (size, node) ->
            val rule = axiz(node.modifier.width, node.modifier.height)
            when (rule) {
                is UiLength.Fill, is UiLength.Available -> 0

                else -> axiz(size.width, size.height)
            }
        }

        val totalGap  = gap * (children.size - 1).coerceAtLeast(0)
        val remaining = (mainAxis - fixedSize - totalGap).coerceAtLeast(0)
        val fillWeight = children.sumOf { node ->
            when (val rule = axiz(node.modifier.width, node.modifier.height)) {
                is UiLength.UiWeighted -> rule.weight.toDouble()
                else -> 0.0
            }
        }

        var cursor = axiz(inner.x, inner.y)

        children.zip(measured).forEach { (node, size) ->
            val mainRule  = axiz(node.modifier.width, node.modifier.height)
            val crossRule = axiz(node.modifier.height, node.modifier.width)

            val mainSize =
                when (mainRule) {
                    is UiLength.Fill, is UiLength.Available ->
                        if (fillWeight <= 0.0)
                            0
                        else
                            (remaining * (mainRule.weight.toDouble() / fillWeight)).toInt()
                    else -> axiz(size.width, size.height)
                }
                    .coerceAtLeast(axiz(node.modifier.minWidth, node.modifier.minHeight))

            val crossSize =
                when (crossRule) {
                    is UiLength.Fill, is UiLength.Available -> axiz(inner.height, inner.width)
                    is UiLength.Expand                      -> axiz(size.height, size.width)
                    else -> axiz(
                        size.height.coerceAtMost(inner.height),
                        size.width.coerceAtMost(inner.width)
                    )
                }
                    .coerceAtLeast(
                        axiz(
                            node.modifier.minHeight,
                            node.modifier.minWidth
                        )
                    )

            val crossAlign = axiz(
                calcAlign(node.modifier.vAlign, inner.height, crossSize),
                calcAlign(node.modifier.hAlign, inner.width, crossSize)
            )

            val childBound = axiz(
                UiRect(cursor, inner.y + crossAlign, mainSize, crossSize),
                UiRect(inner.x + crossAlign, cursor, crossSize, mainSize)
            )

            node.render(runtime, childBound)

            cursor += mainSize + gap
        }
    }

    private fun <T> axiz(horizontal: T, vertical: T): T =
        when (axis) {
            UiAxis.Horizontal -> horizontal
            UiAxis.Vertical   -> vertical
        }
}