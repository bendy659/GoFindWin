package ru.benos.gofindwin.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.ChatFormatting
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import ru.benos.gofindwin.client.GoFindWinClient
import ru.benos_codex.client.gui.auto_layout.AbstractAutoLayoutScreen
import ru.benos_codex.client.gui.auto_layout.Theme
import ru.benos_codex.client.gui.auto_layout.ui.*

@Environment(EnvType.CLIENT)
open class AutoLayoutScreen(title: Component): AbstractAutoLayoutScreen(title) {
    protected var showIDEOverlay: Boolean = false

    private var bodyRow   : Int = 50
    private var leftColumn: Int = 50

    var ideValue: String = ""

    private fun buildIDEHighlightRule() {
        if (GoFindWinClient.ideHighlightRule.isNotEmpty()) return

        fun makeRule(list: String, color: ChatFormatting): UiTextHighlightRule =
            UiTextHighlightRule(pattern = Regex("\\b(?:$list)\\b"), color = color.color!!)

        fun cleanupRegex(list: List<Any>): String =
            list.joinToString("|") { Regex.escape(it.toString()) }

        val listVariables = cleanupRegex(GoFindWinClient.ideVariables)
        val listFunctions = cleanupRegex(GoFindWinClient.ideFunctions)
        val listSpecials  = cleanupRegex(GoFindWinClient.ideSpecials)

        GoFindWinClient.ideHighlightRule = listOf(
            makeRule(listVariables, ChatFormatting.RED),
            makeRule(listFunctions, ChatFormatting.DARK_PURPLE),
            makeRule(listSpecials, ChatFormatting.DARK_AQUA)
        )
    }

    override fun buildUi(): UiNode =
        UiBox(
            modifier = UiModifier.None
                .fillWidth()
                .fillHeight()
                .padding(8),
            backgroundColor = Theme.panelBackground,
            outline = UiOutline(Theme.border, 2),
            children = buildList {
                val baseContent = UiColumn(
                    modifier = UiModifier.None.fillWidth(),
                    gap = 4,
                    children = UiBuilder().apply {
                        label(title, align = UiTextAlign.Start, maxLines = 1)
                        separator(thickness = 2, modifier = UiModifier.None.fillWidth())
                        buildUiContent()
                    }.build()
                )

                add(if (showIDEOverlay) UiPassiveNode(baseContent) else baseContent)

                if (showIDEOverlay) {
                    add(
                        UiBox(
                            modifier = UiModifier.None.fillWidth().fillHeight(),
                            children = UiBuilder().apply {
                                buildIDEOverlay()
                            }.build()
                        )
                    )
                }
            }
        )

    open fun UiBuilder.previewLayout() { }

    open fun UiBuilder.buildIDEOverlay() {
        box(
            modifier = UiModifier.None
                .fillWidth()
                .fillHeight()
                .padding(4),
            backgroundColor = Theme.panelBackground,
            outline = UiOutline(Theme.border, 4)
        ) {
            splitRow(
                value = ::bodyRow,
                modifier = UiModifier.None.fillWidth().fillHeight(),
                gap = 4,
                left = {
                    splitColumn(
                        value = ::leftColumn,
                        modifier = UiModifier.None.fillWidth().fillHeight(),
                        gap = 4,
                        top = { },
                        bottom = { previewLayout() }
                    )
                },
                right = {
                    buildIDEHighlightRule()

                    multilineTextField(
                        value = ::ideValue,
                        modifier = UiModifier.None.fillWidth().fillHeight(),
                        maxChars = 4096,
                        showCharCount = true,
                        highlights = GoFindWinClient.ideHighlightRule
                    )
                }
            )
        }
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && showIDEOverlay) {
            showIDEOverlay = false
            return true
        }

        return super.keyPressed(keyEvent)
    }
}
