package ru.benos_codex.client.gui.auto_layout

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.util.CommonColors
import net.minecraft.world.item.Items
import ru.benos_codex.client.gui.auto_layout.ui.*

class DemoAutoLayoutScreen : AbstractAutoLayoutScreen(Component.literal("Auto Layout Demo")) {
    companion object {
        val show: Unit get() =
            Minecraft.getInstance().setScreen(DemoAutoLayoutScreen())
    }

    private var clickCount: Int = 0
    private var panelSplit: Int = 50
    private var nameValue: String = "Benos"
    private var notesValue: String = "sin(x) = 12\nSecond line"
    private var notesZoom: Float = 1f
    private var retriesValue: Int = 3
    private var thresholdValue: Float = 1.25f
    private var profileValue: String = "Balanced"
    private var featureEnabled: Boolean = true

    private var inputsOpen: Boolean = true
    private var layoutOpen: Boolean = true
    private var actionsOpen: Boolean = true

    private val profileOptions: List<UiDropdownOption<String>> = listOf(
        UiDropdownOption("Fast", Component.literal("Fast")),
        UiDropdownOption("Balanced", Component.literal("Balanced")),
        UiDropdownOption("Quality", Component.literal("Quality"))
    )

    override fun buildUi() = ui {
        box(
            modifier = UiModifier.None
                .fillWidth()
                .fillHeight()
                .padding(12),
            backgroundColor = Theme.panelBackground,
            outline = UiOutline(Theme.border, 1)
        ) {
            scrollArea(
                key = "demo/root-scroll",
                modifier = UiModifier.None.fillWidth().fillHeight(),
                scrollBarWidth = 6,
                scrollGap = 6
            ) {
                column(
                    modifier = UiModifier.None.fillWidth(),
                    gap = 14
                ) {
                    buildHeroSection()
                    buildInputsSection()
                    buildLayoutSection()
                    buildActionsSection()
                }
            }
        }
    }

    private fun UiBuilder.buildHeroSection() {
        box(
            modifier = UiModifier.None.fillWidth().padding(12),
            backgroundColor = ARGB.color(54, 255, 255, 255),
            outline = UiOutline(Theme.border, 1)
        ) {
            column(
                modifier = UiModifier.None.fillWidth(),
                gap = 6
            ) {
                label(
                    text = Component.literal("New UI Builder"),
                    color = CommonColors.YELLOW,
                    modifier = UiModifier.None.fillWidth(),
                    tooltip = tooltip(Component.literal("Demo entry point for the new UI library"))
                )
                label(
                    text = Component.literal(
                        "Demo показывает auto-layout контейнеры, текстовые поля, split layout, group и интерактивные controls."
                    ),
                    wrap = true,
                    modifier = UiModifier.None.fillWidth(),
                    tooltip = tooltip(Component.literal("Short overview of what is showcased below"))
                )
            }
        }
    }

    private fun UiBuilder.buildInputsSection() {
        group(
            title = Component.literal("INPUTS"),
            expanded = ::inputsOpen,
            modifier = UiModifier.None.fillWidth(),
            tooltip = tooltip(Component.literal("Text input widgets and editor-like interactions"))
        ) {
            sectionCard {
                formGrid(rows = 6) {
                    cell(0, 0) {
                        formLabel("Name")
                    }
                    cell(0, 1) {
                        textField(
                            value = ::nameValue,
                            modifier = UiModifier.None.fillWidth(),
                            placeholder = Component.literal("Type a short value"),
                            tooltip = tooltip(Component.literal("Single-line input with clipping and caret"))
                        )
                    }
                    cell(1, 0) {
                        formLabel("Notes")
                    }
                    cell(1, 1) {
                        multilineTextField(
                            value = ::notesValue,
                            zoom = zoom(::notesZoom),
                            modifier = UiModifier.None.fillWidth().fixedHeight(132),
                            placeholder = Component.literal("Ctrl + Wheel changes zoom"),
                            showCharCount = true,
                            maxChars = 220,
                            highlights = listOf(
                                UiTextHighlightRule(Regex("\\bsin\\b"), CommonColors.YELLOW),
                                UiTextHighlightRule(Regex("\\b\\d+\\b"), 0xFF7CFC00.toInt())
                            ),
                            tooltip = tooltip {
                                label(Component.literal("Multiline editor"))
                                label(Component.literal("Ctrl+A/C/V/X, mouse selection, zoom, internal scroll"))
                            }
                        )
                    }
                    cell(2, 0) {
                        formLabel("Retries")
                    }
                    cell(2, 1) {
                        intField(
                            value = ::retriesValue,
                            range = 0..32,
                            step = 1,
                            modifier = UiModifier.None.fixedWidth(96),
                            tooltip = tooltip {
                                label(Component.literal("Integer field"))
                                label(Component.literal("Hover and use mouse wheel to change by 1"))
                            }
                        )
                    }
                    cell(3, 0) {
                        formLabel("Threshold")
                    }
                    cell(3, 1) {
                        floatField(
                            value = ::thresholdValue,
                            range = 0f..10f,
                            step = 0.25f,
                            modifier = UiModifier.None.fixedWidth(110),
                            tooltip = tooltip {
                                label(Component.literal("Float field"))
                                label(Component.literal("Hover and use mouse wheel to change by 0.25"))
                            }
                        )
                    }
                    cell(4, 0) {
                        formLabel("Profile")
                    }
                    cell(4, 1) {
                        dropdown(
                            selected = profileValue,
                            options = profileOptions,
                            onSelect = { option -> profileValue = option.value },
                            modifier = UiModifier.None.fixedWidth(140),
                            tooltip = tooltip {
                                label(Component.literal("Dropdown select"))
                                label(Component.literal("Simple popup list for choosing one value"))
                            }
                        )
                    }
                    cell(5, 0) {
                        formLabel("Enabled")
                    }
                    cell(5, 1) {
                        checkbox(
                            value = ::featureEnabled,
                            text = Component.literal(if (featureEnabled) "Feature is on" else "Feature is off"),
                            modifier = UiModifier.None.wrapWidth(),
                            tooltip = tooltip(Component.literal("Simple boolean toggle"))
                        )
                    }
                }
            }
        }
    }

    private fun UiBuilder.buildLayoutSection() {
        group(
            title = Component.literal("LAYOUT"),
            expanded = ::layoutOpen,
            modifier = UiModifier.None.fillWidth(),
            tooltip = tooltip(Component.literal("Container layouts, grid and split panels"))
        ) {
            sectionCard {
                formGrid(rows = 3) {
                    cell(0, 0) {
                        formLabel("Split")
                    }
                    cell(0, 1) {
                        column(
                            modifier = UiModifier.None.fillWidth(),
                            gap = 6
                        ) {
                            label(Component.literal("Panel ratio: $panelSplit%"))
                            splitRow(
                                value = ::panelSplit,
                                range = 10..90,
                                modifier = UiModifier.None.fillWidth().fixedHeight(190),
                                gap = 8,
                                dividerWidth = 2,
                                minLeftWidth = 140,
                                minRightWidth = 180,
                                tooltip = tooltip(Component.literal("Drag divider to resize left and right panels")),
                                left = {
                                    demoPanel(
                                        title = "Navigator",
                                        background = Theme.surfaceSoft,
                                        body = "Left area keeps structure and summary blocks."
                                    )
                                },
                                right = {
                                    demoPanel(
                                        title = "Workspace",
                                        background = Theme.surfaceAccent,
                                        body = "Right area uses the remaining space and reacts to divider drag."
                                    )
                                }
                            )
                        }
                    }
                    cell(1, 0) {
                        formLabel("Grid")
                    }
                    cell(1, 1) {
                        box(
                            modifier = UiModifier.None.fillWidth().padding(10),
                            backgroundColor = ARGB.color(28, 255, 255, 255),
                            outline = UiOutline(Theme.border, 1)
                        ) {
                            grid(
                                rows = 2,
                                columns = 2,
                                modifier = UiModifier.None.fillWidth(),
                                horizontalGap = 8,
                                verticalGap = 8,
                                columnWeights = listOf(1f, 1f),
                                rowWeights = listOf(1f, 1f)
                            ) {
                                cell(0, 0) { previewTile("A", Theme.surfaceSoft) }
                                cell(0, 1) { previewTile("B", Theme.surfaceAccent) }
                                cell(1, 0) { previewTile("C", Theme.surfaceAccent) }
                                cell(1, 1) { previewTile("D", Theme.surfaceSoft) }
                            }
                        }
                    }
                    cell(2, 0) {
                        formLabel("ItemStack")
                    }
                    cell(2, 1) {
                        row(
                            modifier = UiModifier.None.fillWidth(),
                            gap = 14
                        ) {
                            column(
                                modifier = UiModifier.None.wrapWidth(),
                                gap = 4
                            ) {
                                label(Component.literal("2D"))
                                itemStack(
                                    stack = Items.DIAMOND_SWORD.defaultInstance,
                                    mode = UiItemStackMode.Flat2D,
                                    modifier = UiModifier.None.fixedWidth(22).fixedHeight(22),
                                    tooltip = tooltip(Component.literal("Inventory-style 2D icon render"))
                                )
                            }
                            column(
                                modifier = UiModifier.None.wrapWidth(),
                                gap = 4
                            ) {
                                label(Component.literal("3D"))
                                itemStack(
                                    stack = Items.CLOCK.defaultInstance,
                                    mode = UiItemStackMode.Spin3D,
                                    modifier = UiModifier.None.fixedWidth(30).fixedHeight(30),
                                    tooltip = tooltip(Component.literal("World-like spinning preview render"))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun UiBuilder.buildActionsSection() {
        group(
            title = Component.literal("ACTIONS"),
            expanded = ::actionsOpen,
            modifier = UiModifier.None.fillWidth(),
            tooltip = tooltip(Component.literal("Buttons and general interaction area"))
        ) {
            sectionCard {
                formGrid(rows = 2) {
                    cell(0, 0) {
                        formLabel("Primary")
                    }
                    cell(0, 1) {
                        grid(
                            rows = 1,
                            columns = 2,
                            modifier = UiModifier.None.fillWidth(),
                            horizontalGap = 10,
                            columnWeights = listOf(0.9f, 2.1f)
                        ) {
                            cell(0, 0) {
                                button(
                                    text = Component.literal("Clicks: $clickCount"),
                                    modifier = UiModifier.None.wrapWidth(),
                                    tooltip = tooltip(Component.literal("Pressed state commits on mouse release inside button"))
                                ) {
                                    clickCount++
                                }
                            }
                            cell(0, 1) {
                                label(
                                    text = Component.literal("Release inside button to trigger click."),
                                    wrap = true,
                                    modifier = UiModifier.None.fillWidth(),
                                    tooltip = tooltip(Component.literal("This text explains the button click behavior"))
                                )
                            }
                        }
                    }
                    cell(1, 0) {
                        formLabel("Footer")
                    }
                    cell(1, 1) {
                        label(
                            text = Component.literal("Scroll down to verify scroll area clipping and interactive scrollbar."),
                            wrap = true,
                            modifier = UiModifier.None.fillWidth(),
                            tooltip = tooltip(Component.literal("Root scroll area supports wheel, track click and thumb drag"))
                        )
                    }
                }

                spacer(UiModifier.None.fixedHeight(80))

                label(
                    text = Component.literal("Bottom reached"),
                    align = UiTextAlign.Center,
                    modifier = UiModifier.None.fillWidth(),
                    tooltip = tooltip(Component.literal("End of the scrollable demo content"))
                )
            }
        }
    }

    private fun UiBuilder.sectionCard(
        block: UiBuilder.() -> Unit
    ) {
        box(
            modifier = UiModifier.None.fillWidth().padding(10),
            backgroundColor = ARGB.color(36, 255, 255, 255),
            outline = UiOutline(Theme.border, 1)
        ) {
            column(
                modifier = UiModifier.None.fillWidth(),
                gap = 10,
                block = block
            )
        }
    }

    private fun UiBuilder.formGrid(
        rows: Int,
        block: UiGridBuilder.() -> Unit
    ) {
        grid(
            rows = rows,
            columns = 2,
            modifier = UiModifier.None.fillWidth(),
            horizontalGap = 12,
            verticalGap = 10,
            columnWeights = listOf(0.75f, 2.25f),
            block = block
        )
    }

    private fun UiBuilder.formLabel(text: String) {
        label(
            text = Component.literal(text),
            color = ARGB.color(210, 210, 210, 210),
            modifier = UiModifier.None.fillWidth()
        )
    }

    private fun UiBuilder.demoPanel(
        title: String,
        background: Int,
        body: String
    ) {
        box(
            modifier = UiModifier.None.fillWidth().fillHeight().padding(10),
            backgroundColor = background,
            outline = UiOutline(Theme.border, 1)
        ) {
            column(
                modifier = UiModifier.None.fillWidth(),
                gap = 6
            ) {
                label(
                    text = Component.literal(title),
                    align = UiTextAlign.Center,
                    modifier = UiModifier.None.fillWidth()
                )
                label(
                    text = Component.literal(body),
                    wrap = true,
                    modifier = UiModifier.None.fillWidth()
                )
            }
        }
    }

    private fun UiBuilder.previewTile(
        title: String,
        background: Int
    ) {
        box(
            modifier = UiModifier.None.fillWidth().fixedHeight(44).padding(8),
            backgroundColor = background,
            outline = UiOutline(Theme.border, 1)
        ) {
            label(
                text = Component.literal(title),
                align = UiTextAlign.Center,
                modifier = UiModifier.None.fillWidth()
            )
        }
    }
}
