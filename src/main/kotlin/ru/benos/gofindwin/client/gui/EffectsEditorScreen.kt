package ru.benos.gofindwin.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClient.Translate.mtranslate
import ru.benos.gofindwin.client.data.ParticleDisplayType
import ru.benos.gofindwin.client.data.ParticleItemData
import ru.benos.gofindwin.client.data.ParticleSpawnMode
import ru.benos.gofindwin.client.data.ParticleTextureData
import ru.benos.gofindwin.client.gui.AutoLayoutScreen
import ru.benos_codex.client.gui.auto_layout.ui.*
import kotlin.reflect.KMutableProperty0

class EffectsEditorScreen: AutoLayoutScreen("gui.$MOD_ID.effects.title".mtranslate) {
    companion object {
        val show: Unit get() =
            Minecraft.getInstance().setScreen(EffectsEditorScreen())
    }

    private var mainRow: Int = 75

    private var groupGeneralExpanded: Boolean = true
    private var groupVisualExpanded : Boolean = true

    private var emitterTypeValue: String = "default"
    private val emitterTypeOptions: List<UiDropdownOption<String>> = listOf(
        UiDropdownOption("default", "props.emitter_type.values.default".k),
        UiDropdownOption("new_record", "props.emitter_type.values.new_record".k),
        UiDropdownOption("average", "props.emitter_type.values.average".k),
        UiDropdownOption("bad", "props.emitter_type.values.bad".k)
    )

    private var spawnModeValue: ParticleSpawnMode = ParticleSpawnMode.SINGLE
    private var spawnModeOptions: List<UiDropdownOption<ParticleSpawnMode>> = listOf(
        UiDropdownOption(ParticleSpawnMode.SINGLE, "props.spawn_mode.values.single".k),
        UiDropdownOption(ParticleSpawnMode.CONTINUOUS, "props.spawn_mode.values.continuous".k)
    )

    private var particleCount   : String = "128"
    private var particlePerMin  : String = "120"
    private var particleLifetime: String = "60"

    private var particleDisplayTypeValue  : ParticleDisplayType = ParticleDisplayType.ITEM
    private var particleDisplayTypeOptions: List<UiDropdownOption<ParticleDisplayType>> = listOf(
        UiDropdownOption(ParticleDisplayType.ITEM, "props.display_type.values.item".k),
        UiDropdownOption(ParticleDisplayType.TEXTURE, "props.display_type.values.texture".k),
        UiDropdownOption(ParticleDisplayType.COLOR, "props.display_type.values.color".k),
        UiDropdownOption(ParticleDisplayType.LABEL, "props.display_type.values.label".k)
    )

    private var particleItem   : String = ParticleItemData.ExampleStr
    private var particleTexture: String = ParticleTextureData.ExampleStr
    private var particleColor  : String = "#FFAA00"
    private var particleLabel  : String = "Go\nFind\nWin"

    private var particleItemConstVar   : Boolean = false
    private var particleTextureConstVar: Boolean = false
    private var particleColorConstVar  : Boolean = false
    private var particleLabelConstVar  : Boolean = false

    override fun UiBuilder.buildUiContent() {
        splitRow(
            value = ::mainRow,
            range = 25..75,
            left = {
                column(gap = 4, modifier = UiModifier().fillWidth()) {
                    label(
                        text = "info.label".k,
                        align = UiTextAlign.Center,
                        tooltip = tooltip("info.tooltip".k),
                        modifier = UiModifier().fillWidth()
                    )

                    group("groups.general".k, ::groupGeneralExpanded, modifier = UiModifier().fillWidth().padding(right = 4)) {
                        grid(4, 2, verticalGap = 8) {
                            propOptions(0, "emitter_type", ::emitterTypeValue, emitterTypeOptions)
                            propOptions(1, "spawn_mode", ::spawnModeValue, spawnModeOptions)

                            when (spawnModeValue) {
                                ParticleSpawnMode.SINGLE ->
                                    propString(2, "particle_count", ::particleCount)
                                ParticleSpawnMode.CONTINUOUS ->
                                    propString(2, "particle_per_min", ::particlePerMin)
                            }

                            propString(3, "particle_lifetime", ::particleLifetime)
                        }
                    }

                    group("groups.visual".k, ::groupVisualExpanded, modifier = UiModifier().fillWidth().padding(right = 4)) {
                        grid(4, 2, verticalGap = 8) {
                            propOptions(0, "display_type", ::particleDisplayTypeValue, particleDisplayTypeOptions)

                            when (particleDisplayTypeValue) {
                                ParticleDisplayType.ITEM ->
                                    constVar(1, "item", ::particleItemConstVar, ::particleItem)
                                ParticleDisplayType.TEXTURE ->
                                    constVar(1, "texture", ::particleTextureConstVar, ::particleTexture)
                                ParticleDisplayType.COLOR ->
                                    constVar(1, "color", ::particleColorConstVar, ::particleColor)
                                ParticleDisplayType.LABEL ->
                                    constVar(1, "label", ::particleLabelConstVar, ::particleLabel)
                            }
                        }
                    }
                }
            },
            right = {

            }
        )
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
        prop(index, id) { textField(value, modifier = UiModifier().fillWidth()) }

    private fun UiGridBuilder.constVar(index: Int, id: String, constVar: KMutableProperty0<Boolean>, value: KMutableProperty0<String>) {
        prop(index, id) {
            row(gap = 8, modifier = UiModifier().fillWidth()) {
                val bLabel =
                    if (constVar.get())
                        "gui.$MOD_ID.buttons.expression".mtranslate
                    else
                        "gui.$MOD_ID.buttons.consts".mtranslate

                button(bLabel, modifier = UiModifier().fillWidth(0.35f)) { constVar.set(!constVar.get()) }

                separator(modifier = UiModifier().fillHeight(), thickness = 2)

                if (constVar.get())
                    button("gui.$MOD_ID.buttons.open_ide_editor".mtranslate) { showIDEOverlay = true }
                else
                    textField(value, modifier = UiModifier().fillWidth())
            }
        }
    }
}