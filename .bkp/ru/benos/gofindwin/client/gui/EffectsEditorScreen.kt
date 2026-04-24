package ru.benos.gofindwin.client.gui

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.GoFindWinConst.mident
import ru.benos.gofindwin.client.GoFindWinClient
import ru.benos.gofindwin.client.GoFindWinClientConfig
import ru.benos.gofindwin.client.GoFindWinClientTranslate.mtranslate
import ru.benos.gofindwin.client.data.EffectProfileData
import ru.benos.gofindwin.client.data.FileType
import ru.benos.gofindwin.client.data.ParticleItemData
import ru.benos.gofindwin.client.particle.ParticleManagerBaker
import ru.benos.gofindwin.client.particle.ParticlePlayer
import ru.benos_codex.client.gui.auto_layout.ui.*

@Environment(EnvType.CLIENT)
class EffectsEditorScreen: AutoLayoutScreen("$MOD_ID.gui.effects.labels.title".mtranslate(MOD_ID)) {
    enum class PlaybackState { PLAYING, PAUSED, STOPPED }

    private val ATLAS: Identifier = "textures/gui/atlas.png".mident

    companion object {
        fun show() =
            Minecraft.getInstance().setScreen(EffectsEditorScreen())
    }

    private var mainRow: Int = 50

    private var groupsBakingExpanded: Boolean = false

    private var bakingIterations: Int = 1

    private var rawString: String = ""
    private var zoom: Float = 1.0f

    // Profiles //
    private lateinit var optionsProfiles: List<UiDropdownOption<EffectProfileData>>
    private lateinit var currentProfile: EffectProfileData

    // Textures //
    private val playTexNormal: UiTexture = UiTexture(
        texture = ATLAS, textureWidth = 64, textureHeight = 16,
        regionWidth = 16
    )
    private val playTexHovered: UiTexture = playTexNormal.copy(tintColor = ARGB.color(255, 255, 128))
    private val playTexPressed: UiTexture = playTexNormal.copy(tintColor = ARGB.color(128, 128, 128))

    private val pauseTexNormal: UiTexture = UiTexture(
        texture = ATLAS, textureWidth = 64, textureHeight = 16,
        u = 16f,
        regionWidth = 16
    )
    private val pauseTexHovered: UiTexture = pauseTexNormal.copy(tintColor = ARGB.color(255, 255, 128))
    private val pauseTexPressed: UiTexture = pauseTexNormal.copy(tintColor = ARGB.color(128, 128, 128))

    private val stopTexNormal: UiTexture = UiTexture(
        texture = ATLAS, textureWidth = 64, textureHeight = 16,
        u = 32f,
        regionWidth = 16
    )
    private val stopTexHovered: UiTexture = stopTexNormal.copy(tintColor = ARGB.color(255, 255, 128))
    private val stopTexPressed: UiTexture = stopTexNormal.copy(tintColor = ARGB.color(128, 128, 128))

    private val loopTexNormal: UiTexture = UiTexture(
        texture = ATLAS, textureWidth = 64, textureHeight = 16,
        u = 48f,
        regionWidth = 16
    )
    private val loopTexHovered: UiTexture = loopTexNormal.copy(tintColor = ARGB.color(255, 255, 128))
    private val loopTexPressed: UiTexture = loopTexNormal.copy(tintColor = ARGB.color(128, 128, 128))

    private val playTexButton: UiButtonTextures = UiButtonTextures(
        normal = playTexNormal,
        hovered = playTexHovered,
        pressed = playTexPressed
    )
    private val pauseTexButton: UiButtonTextures = UiButtonTextures(
        normal = pauseTexNormal,
        hovered = pauseTexHovered,
        pressed = pauseTexPressed
    )
    private val stopTexButton: UiButtonTextures = UiButtonTextures(
        normal = stopTexNormal,
        hovered = stopTexHovered,
        pressed = stopTexPressed
    )
    private val loopTexButton: UiButtonTextures = UiButtonTextures(
        normal = loopTexNormal,
        hovered = loopTexHovered,
        pressed = loopTexPressed
    )

    private var playbackState: PlaybackState = PlaybackState.STOPPED
    private val particleManager = ParticlePlayer(ParticleManagerBaker.currentBake)

    init {
        // Get profiles from file config/gofindwin/profiles/effects.json //
        var list = GoFindWinClientConfig.pull<List<EffectProfileData>>(FileType.PROFILES_EFFECTS)
        buildProfiles(list)

        buildHighlightRules()
    }

    override fun UiBuilder.buildUiContent() {
        box(modifier = UiModifier().padding(bottom = 20)) {
            column(
                modifier = UiModifier()
                    .fillWidth()
                    .expandHeight(),
                gap = 4
            ) {
                topBar()
                separator(modifier = UiModifier().fillWidth())
                content()
                separator(modifier = UiModifier().fillWidth())
                bottomBar()
            }
        }
    }

    private fun UiBuilder.topBar() {
        row(
            gap = 16,
            modifier = UiModifier()
                .fillWidth()
                .padding(horizontal = 16)
        ) {
            row(
                gap = 8,
                modifier = UiModifier()
                    .fillWidth()
            ) {
                label(
                    "labels.profile".k,
                    verticalAlign = UiTextVerticalAlign.Center,
                    modifier = UiModifier()
                        .fillHeight()
                )
                dropdown(
                    selected = currentProfile,
                    options = optionsProfiles,
                    onSelect = { option -> currentProfile = option.value },
                    modifier = UiModifier()
                        .fillWidth()
                )
            }
        }
    }

    private fun UiBuilder.content() =
        splitRow(
            value = ::mainRow,
            range = 25..75,
            modifier = UiModifier()
                .fillWidth()
                .availableHeight(),
            left = {
                scrollArea(
                    "scrollArea",
                    modifier = UiModifier()
                        .fillWidth()
                        .fillHeight()
                ) {
                    column(
                        gap = 8,
                        modifier = UiModifier()
                            .fillWidth()
                    ) {
                        multilineTextField(
                            value = ::rawString,
                            zoom = UiZoomBinding(
                                getter = { zoom },
                                setter = { f -> zoom = f }
                            ),
                            showCharCount = true,
                            highlights = GoFindWinClient.ideHighlightRule,
                            modifier = UiModifier()
                                .fillWidth()
                                .expandHeight()
                        )

                        group(
                            title = "groups.baking".k,
                            expanded = ::groupsBakingExpanded,
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
                                    label(
                                        text = "labels.baking.iterations".k,
                                        verticalAlign = UiTextVerticalAlign.Center,
                                        modifier = UiModifier()
                                            .wrapHeight()
                                    )
                                }
                                cell(0, 1) {
                                    intField(
                                        value = ::bakingIterations,
                                        range = 1..8
                                    )
                                }
                            }
                        }
                    }
                }
            },
            right = { previewLayout() }
        )

    private fun UiBuilder.bottomBar() =
        row(
            modifier = UiModifier()
                .fillWidth()
        ) {
            // Right
            button("buttons.bake".k) { baking() }
        }

    private fun UiBuilder.previewLayout() =
        column(
            gap = 4,
            modifier = UiModifier()
                .fillWidth()
                .availableHeight()
                .padding(top = 4)
        ) {
            // Top block //
            label(
                text = "$MOD_ID.gui.effects.labels.particles".mtranslate(particleManager.currentParticleCount),
                verticalAlign = UiTextVerticalAlign.Center,
                modifier = UiModifier()
                    .fillWidth()
            )

            separator(
                modifier = UiModifier()
                    .fillWidth()
            )

            // Middle block //
            box(
                modifier = UiModifier()
                    .fillWidth()
                    .fillHeight()
            ) {
                render(
                    modifier = UiModifier()
                        .fillWidth()
                        .fillHeight()
                ) { guiGraphics, deltaTracker, bounds ->
                    val drawX = (bounds.x + bounds.width / 2).toFloat()
                    val drawY = (bounds.y + bounds.height / 2).toFloat()

                    particleManager.draw(guiGraphics, deltaTracker, drawX, drawY)
                }
            }

            separator(
                modifier = UiModifier()
                    .fillWidth()
            )

            // Bottom block //
            centerNode(
                modifier = UiModifier()
                    .fillWidth()
            ) {
                row(gap = 16) {
                    label(
                        text = "playstates.${playbackState.name.lowercase()}".k,
                        verticalAlign = UiTextVerticalAlign.Center,
                        modifier = UiModifier()
                            .wrapWidth()
                            .fillHeight()
                    )

                    vSeparator(
                        modifier = UiModifier()
                            .fillHeight()
                    )

                    row(gap = 4) {
                        button(
                            text = Component.empty(),
                            textures =
                                when (playbackState) {
                                    PlaybackState.PLAYING -> pauseTexButton
                                    PlaybackState.PAUSED, PlaybackState.STOPPED -> playTexButton
                                }
                        ) {
                            when (playbackState) {
                                PlaybackState.PLAYING -> {
                                    playbackState = PlaybackState.PAUSED
                                    particleManager.ticking = false
                                }
                                PlaybackState.PAUSED  -> {
                                    playbackState = PlaybackState.PLAYING
                                    particleManager.resume()
                                }
                                PlaybackState.STOPPED -> {
                                    playbackState = PlaybackState.PLAYING
                                    particleManager.play()
                                }
                            }
                        }

                        if (playbackState != PlaybackState.STOPPED)
                            button(
                                text = Component.empty(),
                                textures = stopTexButton
                            ) {
                                when (playbackState) {
                                    PlaybackState.PLAYING -> {
                                        playbackState = PlaybackState.STOPPED
                                        particleManager.stop()
                                    }

                                    else -> Unit
                                }

                            }

                        button(
                            text = Component.empty(),
                            textures = loopTexButton
                        ) {
                            particleManager.looping = !particleManager.looping
                        }
                    }

                    vSeparator(
                        modifier = UiModifier()
                            .fillHeight()
                    )

                    label(
                        text = "$MOD_ID.gui.effects.playback.tick".mtranslate(
                            "${particleManager.displayCurrentTick} / ${particleManager.displayTotalTicks}"
                        ),
                        verticalAlign = UiTextVerticalAlign.Center,
                        modifier = UiModifier()
                            .fillHeight()
                    )
                }
            }
        }

    private fun buildProfiles(list: List<EffectProfileData>) {
        // Build new list profiles //
        optionsProfiles = buildList {
            list.forEach { profile ->
                val option = UiDropdownOption(profile, profile.profileName.mtranslate)
                add(option)
            }

            add(UiDropdownOption(EffectProfileData.DEFAULT, "$MOD_ID.profiles.add_new".mtranslate))
            add(UiDropdownOption(EffectProfileData.DEFAULT, "$MOD_ID.profiles.remove".mtranslate))
        }

        // Set default selected profile to first in loaded profiles //
        currentProfile = optionsProfiles.first().value
    }

    private fun baking() {
        val positionScript = rawString.trim()

        val map = buildMap {
            put(ParticleManagerBaker.SourceType.PARTICLE_COUNT, "math.randi(64, 96)")
            put(ParticleManagerBaker.SourceType.PARTICLE_LIFETIME, "math.randi(60, 90)")
            put(ParticleManagerBaker.SourceType.ITEM, ParticleItemData.Example.toString())
            put(ParticleManagerBaker.SourceType.POSITION, positionScript)
            put(ParticleManagerBaker.SourceType.ROTATION, "return vec3(0.0, 0.0, particle.life.age)")
            put(ParticleManagerBaker.SourceType.SCALE, "return vec3(16.0, 16.0, 1.0)")

        }

        ParticleManagerBaker.baking(bakingIterations, map)
        particleManager.setBake(ParticleManagerBaker.currentBake)
        playbackState = PlaybackState.STOPPED
    }

    private fun buildHighlightRules() {
        GoFindWinClient.rebuildIdeHighlightRules()
    }

    override fun tick() {
        super.tick()
        particleManager.tick()
    }

    private val String.k: Component get() =
        "$MOD_ID.gui.effects.${this@k}".mtranslate
}
