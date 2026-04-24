package ru.benos.gofindwin.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import ru.benos_codex.client.gui.auto_layout.AbstractAutoLayoutScreen
import ru.benos_codex.client.gui.auto_layout.Theme
import ru.benos_codex.client.gui.auto_layout.ui.*

@Environment(EnvType.CLIENT)
open class AutoLayoutScreen(title: Component): AbstractAutoLayoutScreen(title) {
    override fun buildUi(): UiNode =
        ui {
            box(
                modifier = UiModifier.None
                    .fillWidth()
                    .fillHeight()
                    .padding(8),
                backgroundColor = Theme.panelBackground,
                outline = UiOutline(Theme.border, 2),
            ) {
                column(
                    gap = 4,
                    modifier = UiModifier.None.fillWidth()
                ) {
                    label(
                        text = title,
                        align = UiTextAlign.Start,
                        maxLines = 1
                    )

                    separator(
                        thickness = 2,
                        modifier = UiModifier.None.fillWidth()
                    )

                    buildUiContent()
                }
            }
        }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return super.keyPressed(keyEvent)
    }
}
