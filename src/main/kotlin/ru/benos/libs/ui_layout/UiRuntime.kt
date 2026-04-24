package ru.benos.libs.ui_layout

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import ru.benos.libs.ui_layout.data.UiSize

class UiRuntime(
    var guiGraphics: GuiGraphics,
    var font       : Font,
    var mouseX     : Int,
    var mouseY     : Int
) {
    private val availableSpaceStack: MutableList<UiSize> = mutableListOf()
    private val overlayRenderers   : MutableList<(Int, Int) -> Unit> = mutableListOf()

    companion object {
        internal var currentRuntime: UiRuntime? = null
    }

    fun startFrame(
        guiGraphics: GuiGraphics,
        font: Font,
        mouseX: Int, mouseY: Int
    ) {
        // Update datas //
        this.guiGraphics = guiGraphics
        this.font = font
        this.mouseX = mouseX; this.mouseY = mouseY

        // Set now UiRuntime //
        currentRuntime = this

        // Cleanup //
        listOf(
            availableSpaceStack,
            overlayRenderers
        ).forEach(MutableList<out Any>::clear)
    }

    // Regions utils //

    // Actions utils //

    // Other utils //
    val currentAvailableWidth : Int?
        get() = availableSpaceStack.lastOrNull()?.width
    val currentAvailableHeight: Int?
        get() = availableSpaceStack.lastOrNull()?.height
}