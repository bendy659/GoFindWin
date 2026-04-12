package ru.benos.gofindwin.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClientTranslate.mtranslate
import ru.benos_codex.client.gui.auto_layout.ui.UiBuilder
import ru.benos_codex.client.gui.auto_layout.ui.UiGridBuilder
import ru.benos_codex.client.gui.auto_layout.ui.UiModifier
import kotlin.reflect.KMutableProperty0

@Environment(EnvType.CLIENT)
class SettingsScreen: AutoLayoutScreen("gui.$MOD_ID.settings.title".mtranslate) {
    companion object {
        fun show() =
            Minecraft.getInstance().setScreen(SettingsScreen())
    }

    private var mainRow: Int = 75

    private var groupGeneralExpanded: Boolean = false
    private var groupExpressionEngineExpanded: Boolean = false
    private var groupMenusExpanded: Boolean = true

    private var expressionEngine_roundToFormula = "%.8f"

    override fun UiBuilder.buildUiContent() {
        splitRow(
            value = ::mainRow,
            range = 25..75,
            left = {
                column(gap = 4, modifier = UiModifier().fillWidth()) {
                    categoryGrid("general", ::groupGeneralExpanded, 2 to 2) {

                    }

                    categoryGrid("expression_engine", ::groupExpressionEngineExpanded, 2 to 2) {

                    }

                    categoryRow("menus", ::groupMenusExpanded) {
                        button("buttons.effects".k) { EffectsEditorScreen.show() }
                        button("buttons.scroller".k) {  }
                    }
                }
            },
            right = {

            }
        )
    }

    private val String.k: Component get() =
        "gui.$MOD_ID.settings.${this@k}".mtranslate

    private fun UiBuilder.category(id: String, expander: KMutableProperty0<Boolean>, block: UiBuilder.() -> Unit) =
        group(
            "groups.$id".k,
            expander,
            modifier = UiModifier()
                .fillWidth()
                .padding(right = 4),
            block = block
        )

    private fun UiBuilder.categoryGrid(id: String, expander: KMutableProperty0<Boolean>, gridSize: Pair<Int, Int>, block: UiGridBuilder.() -> Unit) =
        category(id, expander) {
            grid(gridSize.first, gridSize.second, block = block)
        }

    private fun UiBuilder.categoryRow(id: String, expander: KMutableProperty0<Boolean>, block: UiBuilder.() -> Unit) =
        category(id, expander) {
            row(gap = 16, block = block)
        }
}
