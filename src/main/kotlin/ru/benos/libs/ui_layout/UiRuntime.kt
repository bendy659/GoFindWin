package ru.benos.libs.ui_layout

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.UiSize
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

    private val mouseClickRegions  : MutableSet<UiClickRegion> = mutableSetOf()
    private val mouseReleaseRegions: MutableSet<UiClickRegion> = mutableSetOf()

    private val mouseHoveredRects    : MutableSet<UiRect> = mutableSetOf()
    private val mouseHoveredRectsNext: MutableSet<UiRect> = mutableSetOf()
    private val mouseClickedRects : MutableSet<UiRect> = mutableSetOf()
    private val mouseReleasedRects: MutableSet<UiRect> = mutableSetOf()
    private val mouseReleasedRectsNext: MutableSet<UiRect> = mutableSetOf()

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
    fun addMouseClicked(rect: UiRect, onClicked: (Int, Int, Int) -> Boolean) =
        mouseClickRegions.add(UiClickRegion(rect, onClicked))

    fun addMouseReleased(rect: UiRect, onReleased: (Int, Int, Int) -> Boolean) =
        mouseReleaseRegions.add(UiClickRegion(rect, onReleased))

    // Actions utils //
    fun clicked(key: Int, mouseX: Int, mouseY: Int): Boolean =
        click(key, mouseX, mouseY, mouseClickRegions, mouseClickedRects)

    fun released(key: Int, mouseX: Int, mouseY: Int): Boolean {
        mouseClickedRects.clear() // ← только это остаётся
        return click(key, mouseX, mouseY, mouseReleaseRegions, mouseReleasedRectsNext)
    }

    private fun click(key: Int, mouseX: Int, mouseY: Int, regions: Set<UiClickRegion>, rects: MutableSet<UiRect>): Boolean {
        for (region in regions.reversed()) {
            val condition0 = region.rect.contains(mouseX, mouseY)
            val condition1 = region.clickEvent(key, mouseX, mouseY)

            if (condition0 && condition1) {
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

    fun trackHover(rect: UiRect): Boolean {
        val hovered = rect.contains(mouseX, mouseY)
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