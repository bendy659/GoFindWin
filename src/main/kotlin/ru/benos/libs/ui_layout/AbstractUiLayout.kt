package ru.benos.libs.ui_layout

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.nodes.UiNode

abstract class AbstractUiLayout: Screen {
    constructor(title: Component): super(title)

    private var runtime: UiRuntime? = null

    abstract fun buildUi(): UiNode

    open fun contentBounds(): UiRect =
        UiRect(0, 0, width, height)

    override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(guiGraphics, i, j, f)

        (font as Any? as? Font) ?: return

        val frameRuntime = runtime
            ?: UiRuntime(
                guiGraphics = guiGraphics,
                font = font,
                mouseX = i, mouseY = j
            )
        runtime = frameRuntime

        frameRuntime.startFrame(guiGraphics, font, i, j)

        buildUi().render(frameRuntime, contentBounds())
    }

    // Mouse events //
    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double, g: Double): Boolean {
        return super.mouseScrolled(d, e, f, g)
    }

    // Key input events //
    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return super.keyPressed(keyEvent)
    }
}