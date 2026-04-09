package ru.benos.gofindwin.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClient.Translate.mtranslate
import ru.benos.gofindwin.client.gui.AutoLayoutScreen
import ru.benos_codex.client.gui.auto_layout.ui.UiBuilder
import ru.benos_codex.client.gui.auto_layout.ui.UiModifier

class SettingsScreen: AutoLayoutScreen("gui.$MOD_ID.settings.title".mtranslate) {
    companion object {
        val show: Unit get() =
            Minecraft.getInstance().setScreen(SettingsScreen())
    }

    private var mainRow: Int = 75

    private var groupGeneralExpanded: Boolean = false
    private var groupMenusExpanded: Boolean = true

    private var p0Value : String = "1"

    override fun UiBuilder.buildUiContent() {
        splitRow(
            value = ::mainRow,
            range = 25..75,
            left = {
                column(gap = 4, modifier = UiModifier().fillWidth()) {
                    group("groups.general".k, ::groupGeneralExpanded, modifier = UiModifier().fillWidth().padding(right = 4)) {
                        grid(2, 2) {
                            cell(0, 0) { label("props.p0".k) }
                            cell(0, 1) { textField(::p0Value, modifier = UiModifier().fillWidth()) }
                        }
                    }

                    group("groups.menus".k, ::groupMenusExpanded, modifier = UiModifier().fillWidth().padding(right = 4)) {
                        row(gap = 16) {
                            button("buttons.effects".k) { EffectsEditorScreen.show }
                            button("buttons.scroller".k) {  }
                        }
                    }
                }
            },
            right = {

            }
        )
    }

    private val String.k: Component get() =
        "gui.$MOD_ID.settings.${this@k}".mtranslate
}
