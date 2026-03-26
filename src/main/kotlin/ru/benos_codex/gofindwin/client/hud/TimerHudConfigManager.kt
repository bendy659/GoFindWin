package ru.benos_codex.gofindwin.client.hud

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object TimerHudConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("gofindwin-client.json")

    @Volatile
    var config: TimerHudConfig = TimerHudConfig()
        private set

    fun load() {
        config = readOrCreateConfig()
    }

    private fun readOrCreateConfig(): TimerHudConfig {
        return try {
            if (Files.notExists(configPath)) {
                val defaultConfig = TimerHudConfig()
                writeConfig(defaultConfig)
                defaultConfig
            } else {
                Files.newBufferedReader(configPath).use { reader ->
                    gson.fromJson(reader, TimerHudConfig::class.java) ?: TimerHudConfig()
                }
            }
        } catch (_: IOException) {
            TimerHudConfig()
        } catch (_: JsonSyntaxException) {
            TimerHudConfig()
        }
    }

    private fun writeConfig(config: TimerHudConfig) {
        Files.createDirectories(configPath.parent)
        Files.newBufferedWriter(configPath).use { writer ->
            gson.toJson(config, writer)
        }
    }
}
