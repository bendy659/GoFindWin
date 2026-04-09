package ru.benos.gofindwin.client.gui

import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import ru.benos_codex.client.gui.auto_layout.AbstractAutoLayoutScreen
import ru.benos_codex.client.gui.auto_layout.Theme
import ru.benos_codex.client.gui.auto_layout.ui.*

open class AutoLayoutScreen(title: Component): AbstractAutoLayoutScreen(title) {
    protected var showIDEOverlay: Boolean = false

    override fun buildUi(): UiNode =
        ui {
            box(
                modifier = UiModifier.None
                    .fillWidth()
                    .fillHeight()
                    .padding(8),
                backgroundColor = Theme.panelBackground,
                outline = UiOutline(Theme.border, 2)
            ) {
                column(modifier = UiModifier.None.fillWidth(), gap = 4) {
                    label(title, align = UiTextAlign.Start, maxLines = 1)
                    separator(modifier = UiModifier.None.fillWidth())
                    scrollArea("root/scrollArea") { buildUiContent() }
                }

                if (!showIDEOverlay)
                    buildIDEOverlay()
            }
        }

    open fun UiBuilder.buildIDEOverlay() {

    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && showIDEOverlay) {
            showIDEOverlay = false
            return true
        }

        return super.keyPressed(keyEvent)
    }
}
