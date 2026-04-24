package ru.benos_codex.client.gui.auto_layout.ui

import kotlin.math.max

/** Insets used by layout, clipping and inner content placement. */
data class UiInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    constructor(all: Int) : this(all, all, all, all)
    constructor(horizontal: Int, vertical: Int) : this(horizontal, vertical, horizontal, vertical)

    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom
}

/** Rectangle in screen-space GUI coordinates. */
data class UiRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun shrink(insets: UiInsets): UiRect =
        UiRect(
            x = x + insets.left,
            y = y + insets.top,
            width = (width - insets.horizontal).coerceAtLeast(0),
            height = (height - insets.vertical).coerceAtLeast(0)
        )

    fun contains(px: Double, py: Double): Boolean =
        px >= x.toDouble() && px <= right.toDouble() && py >= y.toDouble() && py <= bottom.toDouble()
}

/** Measured size of a [UiNode]. */
data class UiSize(
    val width: Int,
    val height: Int
)

/** Width or height sizing rule used by [UiModifier]. */
sealed interface UiLength {
    data class Fixed(val value: Int) : UiLength
    data class Fill(val weight: Float = 1f) : UiLength
    data class Available(val weight: Float = 1f) : UiLength
    data class Expand(val weight: Float = 1f) : UiLength
    data object Wrap : UiLength
}

/** Layout modifier describing sizing, padding and minimum size constraints for a node. */
data class UiModifier(
    val width: UiLength = UiLength.Wrap,
    val height: UiLength = UiLength.Wrap,
    val padding: UiInsets = UiInsets(),
    val minWidth: Int = 0,
    val minHeight: Int = 0
) {
    fun fixedWidth(value: Int): UiModifier = copy(width = UiLength.Fixed(value))
    fun fixedHeight(value: Int): UiModifier = copy(height = UiLength.Fixed(value))
    fun fillWidth(weight: Float = 1f): UiModifier = copy(width = UiLength.Fill(weight))
    fun fillHeight(weight: Float = 1f): UiModifier = copy(height = UiLength.Fill(weight))
    fun availableWidth(weight: Float = 1f): UiModifier = copy(width = UiLength.Available(weight))
    fun availableHeight(weight: Float = 1f): UiModifier = copy(height = UiLength.Available(weight))
    fun expandWidth(weight: Float = 1f): UiModifier = copy(width = UiLength.Expand(weight))
    fun expandHeight(weight: Float = 1f): UiModifier = copy(height = UiLength.Expand(weight))
    fun wrapWidth(): UiModifier = copy(width = UiLength.Wrap)
    fun wrapHeight(): UiModifier = copy(height = UiLength.Wrap)
    fun padding(all: Int = 0): UiModifier = copy(padding = UiInsets(all))
    fun padding(horizontal: Int = 0, vertical: Int = 0): UiModifier = copy(padding = UiInsets(horizontal, vertical))
    fun padding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): UiModifier =
        copy(padding = UiInsets(left, top, right, bottom))

    fun minSize(width: Int = minWidth, height: Int = minHeight): UiModifier =
        copy(minWidth = width, minHeight = height)

    companion object {
        val None: UiModifier = UiModifier()
    }
}

internal fun UiModifier.resolveWidth(contentWidth: Int, availableWidth: Int): Int =
    max(
        minWidth,
        when (val rule = width) {
            is UiLength.Fixed -> rule.value
            is UiLength.Fill -> availableWidth
            is UiLength.Available -> UiRuntime.currentRuntime?.currentAvailableWidth() ?: availableWidth
            is UiLength.Expand -> max(
                contentWidth + padding.horizontal,
                UiRuntime.currentRuntime?.currentAvailableWidth() ?: availableWidth
            )
            UiLength.Wrap -> contentWidth + padding.horizontal
        }
    )

internal fun UiModifier.resolveHeight(contentHeight: Int, availableHeight: Int): Int =
    max(
        minHeight,
        when (val rule = height) {
            is UiLength.Fixed -> rule.value
            is UiLength.Fill -> availableHeight
            is UiLength.Available -> UiRuntime.currentRuntime?.currentAvailableHeight() ?: availableHeight
            is UiLength.Expand -> max(
                contentHeight + padding.vertical,
                UiRuntime.currentRuntime?.currentAvailableHeight() ?: availableHeight
            )
            UiLength.Wrap -> contentHeight + padding.vertical
        }
    )
