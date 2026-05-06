package ru.benos.libs.ui_layout

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
import ru.benos.libs.ui_layout.data.UiTransform
import ru.benos.libs.ui_layout.data.region.UiClickRegion

class UiRuntime(
    var guiGraphics: GuiGraphics,
    var font       : Font,
    var mouseX     : Int,
    var mouseY     : Int
) {
    companion object { internal var currentRuntime: UiRuntime? = null }

    // States //
    private val availableSpaceStack: MutableList<UiSize> = mutableListOf()
    private val overlayRenderers   : MutableList<(Int, Int) -> Unit> = mutableListOf()

    private val mouseClickRegions  : MutableList<UiClickRegion> = mutableListOf()
    private val mouseReleaseRegions: MutableList<UiClickRegion> = mutableListOf()

    private val mouseHoveredRects    : MutableSet<UiRect> = mutableSetOf()
    private val mouseHoveredRectsNext: MutableSet<UiRect> = mutableSetOf()
    private val mouseClickedRects : MutableSet<UiRect> = mutableSetOf()
    private val mouseReleasedRects: MutableSet<UiRect> = mutableSetOf()
    private val mouseReleasedRectsNext: MutableSet<UiRect> = mutableSetOf()

    var deltaTime: Float = 0f
    var totalTime: Float = 0f

    fun newFrame(
        guiGraphics: GuiGraphics,
        font: Font,
        mouseX: Int, mouseY: Int
    ) {
        // Cleanup //
        availableSpaceStack.clear()
        overlayRenderers.clear()

        mouseClickRegions.clear()
        mouseReleaseRegions.clear()

        mouseHoveredRects.clear()
        mouseHoveredRects += mouseHoveredRectsNext
        mouseHoveredRectsNext.clear()

        mouseReleasedRects.clear()
        mouseReleasedRects += mouseReleasedRectsNext
        mouseReleasedRectsNext.clear()

        // Update datas //
        this.guiGraphics = guiGraphics
        this.font = font
        this.mouseX = mouseX; this.mouseY = mouseY

        // Set now UiRuntime //
        currentRuntime = this
    }

    // Regions utils //
    fun addMouseClicked(rect: UiRect, transform: UiTransform, onClicked: (Int, Int, Int) -> Boolean) =
        mouseClickRegions.add(UiClickRegion(rect, transform, onClicked))

    fun addMouseReleased(rect: UiRect, transform: UiTransform, onReleased: (Int, Int, Int) -> Boolean) =
        mouseReleaseRegions.add(UiClickRegion(rect, transform, onReleased))

    // Actions utils //
    fun clicked(key: Int, mouseX: Int, mouseY: Int): Boolean =
        click(key, mouseX, mouseY, mouseClickRegions, mouseClickedRects)

    fun released(key: Int, mouseX: Int, mouseY: Int): Boolean {
        mouseClickedRects.clear()
        return click(key, mouseX, mouseY, mouseReleaseRegions, mouseReleasedRectsNext)
    }

    private fun click(key: Int, mouseX: Int, mouseY: Int, regions: List<UiClickRegion>, rects: MutableSet<UiRect>): Boolean {
        for (region in regions.reversed()) {
            val (localX, localY) = region.transform.normalizeMouse(mouseX.toFloat(), mouseY.toFloat(), region.rect)
            if (!region.rect.contains(localX.toDouble(), localY.toDouble()))
                continue

            if (region.clickEvent(key, localX.toInt(), localY.toInt())) {
                rects += region.rect
                return true
            }
        }
        return false
    }

    fun isHovered(rect: UiRect): Boolean =
        mouseHoveredRects.contains(rect)

    fun isClicked(rect: UiRect): Boolean =
        mouseClickedRects.contains(rect)

    fun isReleased(rect: UiRect): Boolean =
        mouseReleasedRects.contains(rect)

    fun trackHover(rect: UiRect, mouseX: Float, mouseY: Float): Boolean {
        val hovered = rect.contains(mouseX.toDouble(), mouseY.toDouble())
        if (hovered)
            mouseHoveredRectsNext += rect

        return hovered
    }

    // Other utils //
    val currentAvailableWidth : Int?
        get() = availableSpaceStack.lastOrNull()?.width
    val currentAvailableHeight: Int?
        get() = availableSpaceStack.lastOrNull()?.height
}
