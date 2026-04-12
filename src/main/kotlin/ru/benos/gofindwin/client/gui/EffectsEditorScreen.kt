package ru.benos.gofindwin.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClientTranslate.mtranslate
import ru.benos.gofindwin.client.ParticleManager
import ru.benos_codex.client.gui.auto_layout.ui.*
import kotlin.reflect.KMutableProperty0

@Environment(EnvType.CLIENT)
class EffectsEditorScreen: AutoLayoutScreen("gui.$MOD_ID.effects.title".mtranslate) {
    companion object {
        fun show() =
            Minecraft.getInstance().setScreen(EffectsEditorScreen())
    }

    private var mainRow: Int = 50

    private var groupGeneralExpanded: Boolean = true
    private var groupVisualExpanded : Boolean = true

    override fun UiBuilder.buildUiContent() {
        box(modifier = UiModifier().padding(bottom = 20)) {
            column(
                modifier = UiModifier()
                    .fillWidth()
                    .expandHeight(),
                gap = 4
            ) {
                content()
                separator(thickness = 2, modifier = UiModifier().fillWidth())
                bottomBar()
            }
        }
    }

    private fun UiBuilder.content() {
        scrollArea(
            "scrollArea",
            modifier = UiModifier()
                .fillWidth()
                .fillHeight()
        ) {
            splitRow(
                value = ::mainRow,
                range = 25..75,
                modifier = UiModifier()
                    .fillWidth()
                    .expandHeight(),
                left = {
                    column(
                        gap = 16,
                        modifier = UiModifier()
                            .fillWidth()
                    ) {
                        row(modifier = UiModifier().fillWidth()) {
                            row(modifier = UiModifier().fillWidth()) {
                                label("labels.profile".k)
                            }

                            vSeparator()

                            row(modifier = UiModifier().fillWidth()) {
                                label("labels.layer".k)
                            }
                        }

                        spacer(modifier = UiModifier().fillWidth())

                        group(
                            title = "groups.general".k,
                            expanded = ::groupGeneralExpanded,
                            modifier = UiModifier()
                                .fillWidth()
                        ) {
                            grid(
                                rows = 2,
                                columns = 2,
                                modifier = UiModifier()
                                    .fillWidth()
                            ) {
                                cell(0, 0) {

                                }
                            }
                        }
                    }
                },
                right = { previewLayout() }
            )
        }
    }

    private fun UiBuilder.bottomBar() {
        row(
            modifier = UiModifier()
                .fillWidth()
        ) {
            // Right
            button("buttons.bake".k) {
                val map = buildMap<ParticleManager.SourceType, String> {

                }

                ParticleManager.baking(map)
            }
        }
    }

    private fun loadProfile() {

    }

    private fun saveProfile() {

    }

    private val String.k: Component get() =
        "gui.$MOD_ID.effects.${this@k}".mtranslate

    private fun UiGridBuilder.prop(index: Int, id: String, block: UiBuilder.() -> Unit) {
        cell(index, 0) { label("props.$id.label".k, tooltip = tooltip("props.$id.tooltip".k)) }
        cell(index, 1, block)
    }

    private fun <T> UiGridBuilder.propOptions(index: Int, id: String, value: KMutableProperty0<T>, options: List<UiDropdownOption<T>>) =
        prop(index, id) { dropdown(value, options, modifier = UiModifier().fillWidth()) }

    private fun UiGridBuilder.propString(index: Int, id: String, value: KMutableProperty0<String>) =
        prop(index, id) { textField(value, modifier = UiModifier().fillWidth(), onClick = {}) }
}
