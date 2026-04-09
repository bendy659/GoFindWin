package ru.benos_codex.client.gui.auto_layout

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import ru.benos_codex.client.gui.auto_layout.ui.UiBuilder
import ru.benos_codex.client.gui.auto_layout.ui.UiNode
import ru.benos_codex.client.gui.auto_layout.ui.UiRect
import ru.benos_codex.client.gui.auto_layout.ui.UiRuntime
import ru.benos_codex.client.gui.auto_layout.ui.ui

abstract class AbstractAutoLayoutScreen(title: Component) : Screen(title) {
    private var runtime: UiRuntime? = null

    protected open fun UiBuilder.buildUiContent() = Unit

    open fun buildUi(): UiNode = ui { buildUiContent() }

    open fun contentBounds(): UiRect {
        val w = (width - 48).coerceAtLeast(0)
        val h = (height - 48).coerceAtLeast(0)

        return UiRect(24, 24, w, h)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        val frameRuntime = runtime ?: UiRuntime(guiGraphics, font, mouseX, mouseY).also { runtime = it }
        frameRuntime.beginFrame(guiGraphics, font, mouseX, mouseY)

        buildUi().render(frameRuntime, contentBounds())

        guiGraphics.renderDeferredElements()

        frameRuntime.renderOverlays(width, height)
        frameRuntime.renderTooltipOverlay(width, height)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        if (mouseButtonEvent.button() == 0 && runtime?.click(mouseButtonEvent.x(), mouseButtonEvent.y()) == true) {
            return true
        }
        if (mouseButtonEvent.button() == 0) {
            runtime?.clearFocus()
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (runtime?.drag(mouseButtonEvent.x(), mouseButtonEvent.y()) == true) {
            return true
        }
        return super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        if (mouseButtonEvent.button() == 0) {
            runtime?.release(mouseButtonEvent.x(), mouseButtonEvent.y())
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (runtime?.charTyped(characterEvent.codepoint().toChar()) == true) {
            return true
        }
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (runtime?.keyPressed(keyEvent.key(), minecraft.hasControlDown(), minecraft?.hasShiftDown() == true) == true) {
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (runtime?.mouseScrolled(scrollY, minecraft.hasControlDown()) == true) {
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
