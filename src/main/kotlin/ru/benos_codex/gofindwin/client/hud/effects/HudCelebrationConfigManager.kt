package ru.benos_codex.gofindwin.client.hud.effects

import com.google.gson.*
import com.google.gson.JsonSyntaxException
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.Type
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

object HudCelebrationConfigManager {
    private val gson = GsonBuilder()
        .registerTypeAdapter(HudCelebrationEmitterConfig::class.java, EmitterConfigAdapter())
        .setPrettyPrinting()
        .create()
    private val effectsDir: Path = FabricLoader.getInstance().configDir.resolve("gofindwin").resolve("effects")
    private val exampleConfigPath: Path = effectsDir.resolve("example.json")

    @Volatile
    var presets: Map<String, HudCelebrationConfig> = defaultPresetMap()
        private set

    fun load() {
        presets = readOrCreatePresets()
    }

    fun getPreset(id: String?): HudCelebrationConfig =
        presets[id] ?: presets["average"] ?: HudCelebrationConfig()

    fun savePreset(id: String, config: HudCelebrationConfig) {
        val updatedPresets = linkedMapOf<String, HudCelebrationConfig>()
        updatedPresets.putAll(defaultPresetMap())
        updatedPresets.putAll(presets)
        updatedPresets[id] = config

        try {
            Files.createDirectories(effectsDir)
            Files.newBufferedWriter(exampleConfigPath).use { writer ->
                gson.toJson(HudCelebrationPresetFile(updatedPresets), writer)
            }
            presets = updatedPresets
        } catch (_: IOException) {
        }
    }

    private fun readOrCreatePresets(): Map<String, HudCelebrationConfig> {
        return try {
            Files.createDirectories(effectsDir)
            ensureDefaultPresetFile()

            val loaded = linkedMapOf<String, HudCelebrationConfig>()
            Files.list(effectsDir).use { paths ->
                paths.forEach { path ->
                    if (!Files.isRegularFile(path) || !path.extension.equals("json", ignoreCase = true)) {
                        return@forEach
                    }

                    val filePresets = readPresetFile(path) ?: return@forEach
                    loaded.putAll(filePresets)
                }
            }

            if (loaded.isEmpty()) defaultPresetMap() else loaded
        } catch (_: IOException) {
            defaultPresetMap()
        }
    }

    private fun readPresetFile(path: Path): Map<String, HudCelebrationConfig>? {
        return try {
            Files.newBufferedReader(path).use { reader ->
                val parsed = gson.fromJson(reader, HudCelebrationPresetFile::class.java) ?: return null
                parsed.presets
            }
        } catch (_: IOException) {
            null
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun ensureDefaultPresetFile() {
        if (Files.exists(exampleConfigPath)) return

        val payload = HudCelebrationPresetFile(defaultPresetMap())
        Files.newBufferedWriter(exampleConfigPath).use { writer ->
            gson.toJson(payload, writer)
        }
    }

    private fun defaultPresetMap(): Map<String, HudCelebrationConfig> = linkedMapOf(
        "new_record" to HudCelebrationConfig(
            emitters = listOf(
                HudCelebrationEmitterConfig(
                    spawnMode = HudCelebrationSpawnMode.SINGLE,
                    count = 60,
                    gravity = 26f,
                    randomOffsetX = 96f,
                    randomOffsetY = 34f,
                    startSizeMin = 9f,
                    startSizeMax = 16f,
                    endSizeMin = 3f,
                    endSizeMax = 8f,
                    lifetimeTicks = 56
                )
            )
        ),
        "average" to HudCelebrationConfig(
            emitters = listOf(HudCelebrationEmitterConfig())
        ),
        "worse" to HudCelebrationConfig(
            emitters = listOf(
                HudCelebrationEmitterConfig(
                    spawnMode = HudCelebrationSpawnMode.SINGLE,
                    count = 23,
                    gravity = 52f,
                    randomOffsetX = 52f,
                    randomOffsetY = 18f,
                    startSizeMin = 5f,
                    startSizeMax = 10f,
                    endSizeMin = 1f,
                    endSizeMax = 4f,
                    lifetimeTicks = 28,
                    colorPalette = listOf("#B6C2CF", "#D7DEE6", "#F6D84E")
                )
            )
        )
    )

    private class EmitterConfigAdapter : JsonSerializer<HudCelebrationEmitterConfig>, JsonDeserializer<HudCelebrationEmitterConfig> {
        override fun serialize(src: HudCelebrationEmitterConfig, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val emitter = prunedForSave(src)
            return JsonObject().apply {
                addProperty("enabled", emitter.enabled)
                addProperty("spawnMode", emitter.spawnMode.name)
                addProperty("renderType", emitter.renderType.name)
                addProperty("originX", emitter.originX)
                addProperty("originXExpression", emitter.originXExpression)
                addProperty("originY", emitter.originY)
                addProperty("originYExpression", emitter.originYExpression)

                when (emitter.spawnMode) {
                    HudCelebrationSpawnMode.SINGLE -> {
                        addProperty("count", emitter.count)
                        addProperty("countExpression", emitter.countExpression)
                    }
                    HudCelebrationSpawnMode.CONTINUOUS -> {
                        addProperty("spawnPeriodMs", emitter.spawnPeriodMs)
                        addProperty("spawnPeriodExpression", emitter.spawnPeriodExpression)
                    }
                }

                addProperty("lifetimeTicks", emitter.lifetimeTicks)
                addProperty("lifetimeExpression", emitter.lifetimeExpression)
                addProperty("positionX", emitter.positionX)
                addProperty("positionXExpression", emitter.positionXExpression)
                addProperty("positionY", emitter.positionY)
                addProperty("positionYExpression", emitter.positionYExpression)
                addProperty("initialRotation", emitter.initialRotation)
                addProperty("initialRotationExpression", emitter.initialRotationExpression)
                addProperty("size", emitter.size)
                addProperty("sizeExpression", emitter.sizeExpression)
                addProperty("alpha", emitter.alpha)
                addProperty("alphaExpression", emitter.alphaExpression)

                when (emitter.renderType) {
                    HudCelebrationRenderType.PARTICLE -> {
                        add("colorPalette", context.serialize(emitter.colorPalette))
                    }
                    HudCelebrationRenderType.TEXTURE -> {
                        addProperty("textureMode", emitter.textureMode.name)
                        addProperty("textureId", emitter.textureId)
                        addProperty("textureUvX", emitter.textureUvX)
                        addProperty("textureUvXExpression", emitter.textureUvXExpression)
                        addProperty("textureUvY", emitter.textureUvY)
                        addProperty("textureUvYExpression", emitter.textureUvYExpression)
                        addProperty("textureUvWidth", emitter.textureUvWidth)
                        addProperty("textureUvWidthExpression", emitter.textureUvWidthExpression)
                        addProperty("textureUvHeight", emitter.textureUvHeight)
                        addProperty("textureUvHeightExpression", emitter.textureUvHeightExpression)
                        addProperty("textureTintColor", emitter.textureTintColor)
                        if (emitter.textureMode == HudCelebrationTextureMode.SHEET) {
                            addProperty("textureFramesX", emitter.textureFramesX)
                            addProperty("textureFramesXExpression", emitter.textureFramesXExpression)
                            addProperty("textureFramesY", emitter.textureFramesY)
                            addProperty("textureFramesYExpression", emitter.textureFramesYExpression)
                        }
                        if (emitter.textureMode != HudCelebrationTextureMode.SINGLE) {
                            addProperty("textureFrameTimeMs", emitter.textureFrameTimeMs)
                            addProperty("textureFrameTimeMsExpression", emitter.textureFrameTimeMsExpression)
                            addProperty("textureInterpolate", emitter.textureInterpolate)
                            addProperty("textureFrameOrder", emitter.textureFrameOrder)
                        }
                    }
                    HudCelebrationRenderType.ITEMSTACK -> {
                        addProperty("itemId", emitter.itemId)
                        addProperty("useTargetItem", emitter.useTargetItem)
                    }
                    HudCelebrationRenderType.TEXT -> {
                        addProperty("textContent", emitter.textContent)
                        addProperty("textColor", emitter.textColor)
                        addProperty("textShadowEnabled", emitter.textShadowEnabled)
                        addProperty("textShadowColor", emitter.textShadowColor)
                        addProperty("textScale", emitter.textScale)
                        addProperty("textScaleExpression", emitter.textScaleExpression)
                    }
                    else -> {}
                }
            }
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): HudCelebrationEmitterConfig {
            val obj = json.asJsonObject
            val base = HudCelebrationEmitterConfig()
            fun JsonObject.string(name: String, fallback: String) = if (has(name)) get(name).asString else fallback
            fun JsonObject.bool(name: String, fallback: Boolean) = if (has(name)) get(name).asBoolean else fallback
            fun JsonObject.int(name: String, fallback: Int) = if (has(name)) get(name).asInt else fallback
            fun JsonObject.float(name: String, fallback: Float) = if (has(name)) get(name).asFloat else fallback
            fun JsonObject.stringList(name: String, fallback: List<String>) =
                if (has(name)) getAsJsonArray(name).map { it.asString } else fallback
            val legacyOrigin = obj.string("origin", "")
            val legacyOriginExpressions = when (legacyOrigin) {
                "HUD_TOP" -> "hud_center_x" to "hud_top"
                "HUD_BOTTOM" -> "hud_center_x" to "hud_bottom"
                "HUD_LEFT" -> "hud_left" to "hud_center_y"
                "HUD_RIGHT" -> "hud_right" to "hud_center_y"
                "HUD_CENTER" -> "hud_center_x" to "hud_center_y"
                else -> null
            }

            return base.copy(
                enabled = obj.bool("enabled", base.enabled),
                spawnMode = obj.string("spawnMode", base.spawnMode.name).let { runCatching { HudCelebrationSpawnMode.valueOf(it) }.getOrDefault(base.spawnMode) },
                renderType = obj.string("renderType", base.renderType.name).let { runCatching { HudCelebrationRenderType.valueOf(it) }.getOrDefault(base.renderType) },
                originX = obj.float("originX", base.originX),
                originXExpression = obj.string("originXExpression", legacyOriginExpressions?.first ?: base.originXExpression),
                originY = obj.float("originY", base.originY),
                originYExpression = obj.string("originYExpression", legacyOriginExpressions?.second ?: base.originYExpression),
                count = obj.int("count", base.count),
                countExpression = obj.string("countExpression", base.countExpression),
                spawnPeriodMs = obj.float("spawnPeriodMs", base.spawnPeriodMs),
                spawnPeriodExpression = obj.string("spawnPeriodExpression", base.spawnPeriodExpression),
                lifetimeTicks = obj.int("lifetimeTicks", base.lifetimeTicks),
                lifetimeExpression = obj.string("lifetimeExpression", base.lifetimeExpression),
                positionX = obj.float("positionX", base.positionX),
                positionXExpression = obj.string("positionXExpression", base.positionXExpression),
                positionY = obj.float("positionY", base.positionY),
                positionYExpression = obj.string("positionYExpression", base.positionYExpression),
                initialRotation = obj.float("initialRotation", base.initialRotation),
                initialRotationExpression = obj.string("initialRotationExpression", base.initialRotationExpression),
                size = obj.float("size", base.size),
                sizeExpression = obj.string("sizeExpression", base.sizeExpression),
                alpha = obj.float("alpha", base.alpha),
                alphaExpression = obj.string("alphaExpression", base.alphaExpression),
                colorPalette = obj.stringList("colorPalette", base.colorPalette),
                colorLerpPalette = obj.stringList("colorLerpPalette", base.colorLerpPalette),
                colorLerpFactorExpression = obj.string("colorLerpFactorExpression", base.colorLerpFactorExpression),
                textureMode = obj.string("textureMode", base.textureMode.name).let { runCatching { HudCelebrationTextureMode.valueOf(it) }.getOrDefault(base.textureMode) },
                textureId = obj.string("textureId", base.textureId),
                textureUvX = obj.int("textureUvX", base.textureUvX),
                textureUvXExpression = obj.string("textureUvXExpression", base.textureUvXExpression),
                textureUvY = obj.int("textureUvY", base.textureUvY),
                textureUvYExpression = obj.string("textureUvYExpression", base.textureUvYExpression),
                textureUvWidth = obj.int("textureUvWidth", base.textureUvWidth),
                textureUvWidthExpression = obj.string("textureUvWidthExpression", base.textureUvWidthExpression),
                textureUvHeight = obj.int("textureUvHeight", base.textureUvHeight),
                textureUvHeightExpression = obj.string("textureUvHeightExpression", base.textureUvHeightExpression),
                textureTintColor = obj.string("textureTintColor", base.textureTintColor),
                textureTintLerpPalette = obj.stringList("textureTintLerpPalette", base.textureTintLerpPalette),
                textureTintLerpFactorExpression = obj.string("textureTintLerpFactorExpression", base.textureTintLerpFactorExpression),
                textureFramesX = obj.int("textureFramesX", base.textureFramesX),
                textureFramesXExpression = obj.string("textureFramesXExpression", base.textureFramesXExpression),
                textureFramesY = obj.int("textureFramesY", base.textureFramesY),
                textureFramesYExpression = obj.string("textureFramesYExpression", base.textureFramesYExpression),
                textureFrameTimeMs = obj.float("textureFrameTimeMs", base.textureFrameTimeMs),
                textureFrameTimeMsExpression = obj.string("textureFrameTimeMsExpression", base.textureFrameTimeMsExpression),
                textureInterpolate = obj.bool("textureInterpolate", base.textureInterpolate),
                textureFrameOrder = obj.string("textureFrameOrder", base.textureFrameOrder),
                itemId = obj.string("itemId", base.itemId),
                useTargetItem = obj.bool("useTargetItem", base.useTargetItem),
                textContent = obj.string("textContent", base.textContent),
                textColor = obj.string("textColor", base.textColor),
                textShadowEnabled = obj.bool("textShadowEnabled", base.textShadowEnabled),
                textShadowColor = obj.string("textShadowColor", base.textShadowColor),
                textScale = obj.float("textScale", base.textScale),
                textScaleExpression = obj.string("textScaleExpression", base.textScaleExpression)
            )
        }

        private fun prunedForSave(emitter: HudCelebrationEmitterConfig): HudCelebrationEmitterConfig {
            val base = HudCelebrationEmitterConfig(
                enabled = emitter.enabled,
                spawnMode = emitter.spawnMode,
                renderType = emitter.renderType,
                originX = emitter.originX,
                originXExpression = emitter.originXExpression,
                originY = emitter.originY,
                originYExpression = emitter.originYExpression,
                count = emitter.count,
                countExpression = emitter.countExpression,
                spawnPeriodMs = emitter.spawnPeriodMs,
                spawnPeriodExpression = emitter.spawnPeriodExpression,
                lifetimeTicks = emitter.lifetimeTicks,
                lifetimeExpression = emitter.lifetimeExpression,
                positionX = emitter.positionX,
                positionXExpression = emitter.positionXExpression,
                positionY = emitter.positionY,
                positionYExpression = emitter.positionYExpression,
                initialRotation = emitter.initialRotation,
                initialRotationExpression = emitter.initialRotationExpression,
                size = emitter.size,
                sizeExpression = emitter.sizeExpression,
                alpha = emitter.alpha,
                alphaExpression = emitter.alphaExpression
            )
            return when (emitter.renderType) {
                HudCelebrationRenderType.PARTICLE -> base.copy(
                    colorPalette = emitter.colorPalette,
                    colorLerpPalette = emitter.colorLerpPalette,
                    colorLerpFactorExpression = emitter.colorLerpFactorExpression
                )
                HudCelebrationRenderType.TEXTURE -> base.copy(
                    textureMode = emitter.textureMode,
                    textureId = emitter.textureId,
                    textureUvX = emitter.textureUvX,
                    textureUvXExpression = emitter.textureUvXExpression,
                    textureUvY = emitter.textureUvY,
                    textureUvYExpression = emitter.textureUvYExpression,
                    textureUvWidth = emitter.textureUvWidth,
                    textureUvWidthExpression = emitter.textureUvWidthExpression,
                    textureUvHeight = emitter.textureUvHeight,
                    textureUvHeightExpression = emitter.textureUvHeightExpression,
                    textureTintColor = emitter.textureTintColor,
                    textureFramesX = emitter.textureFramesX,
                    textureFramesXExpression = emitter.textureFramesXExpression,
                    textureFramesY = emitter.textureFramesY,
                    textureFramesYExpression = emitter.textureFramesYExpression,
                    textureFrameTimeMs = emitter.textureFrameTimeMs,
                    textureFrameTimeMsExpression = emitter.textureFrameTimeMsExpression,
                    textureInterpolate = emitter.textureInterpolate,
                    textureFrameOrder = emitter.textureFrameOrder
                )
                HudCelebrationRenderType.ITEMSTACK -> base.copy(
                    itemId = emitter.itemId,
                    useTargetItem = emitter.useTargetItem
                )
                HudCelebrationRenderType.TEXT -> base.copy(
                    textContent = emitter.textContent,
                    textColor = emitter.textColor,
                    textShadowEnabled = emitter.textShadowEnabled,
                    textShadowColor = emitter.textShadowColor,
                    textScale = emitter.textScale,
                    textScaleExpression = emitter.textScaleExpression
                )
                else -> base
            }
        }
    }
}
