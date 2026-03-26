package ru.benos_codex.gofindwin.client.hud.effects

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

object HudCelebrationConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
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
}
