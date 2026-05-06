package ru.benos.libs.ui_layout

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import ru.benos.libs.ui_layout.builder.UiBuilder
import ru.benos.libs.ui_layout.data.UiModifier
import ru.benos.libs.ui_layout.data.UiRect
import ru.benos.libs.ui_layout.data.theme.UiBoxColorTheme
import ru.benos.libs.ui_layout.nodes.UiBoxNode
import ru.benos.libs.ui_layout.nodes.UiNode

abstract class AbstractUiLayout(title: Component) : Screen(title) {
    protected var runtime: UiRuntime? = null

    abstract fun UiBuilder.ui()

    private fun buildUi(): UiNode =
        UiBoxNode(
            boxTheme = UiBoxColorTheme.TRANSPARENT,
            children = UiBuilder().apply { ui() }.build(),
            enableScissor = false,
            modifier = UiModifier
        )

    open fun contentBounds(): UiRect =
        UiRect(0, 0, width, height)

    override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(guiGraphics, i, j, f)

        val frameRuntime = runtime
            ?: UiRuntime(
                guiGraphics = guiGraphics,
                font = font,
                mouseX = i, mouseY = j
            )
        runtime = frameRuntime

        frameRuntime.deltaTime = f
        frameRuntime.totalTime += frameRuntime.deltaTime / 20.0f
        frameRuntime.newFrame(guiGraphics, font, i, j)

        buildUi().render(frameRuntime, contentBounds())
    }

    // Mouse events //
    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
        val click = runtime?.clicked(
            mouseButtonEvent.button(),
            mouseButtonEvent.x.toInt(),
            mouseButtonEvent.y.toInt()
        )

        if (click == true)
            return true

        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, d: Double, e: Double): Boolean {
        return super.mouseDragged(mouseButtonEvent, d, e)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        val click = runtime?.released(
            mouseButtonEvent.button(),
            mouseButtonEvent.x.toInt(),
            mouseButtonEvent.y.toInt()
        )

        if (click == true)
            return true

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