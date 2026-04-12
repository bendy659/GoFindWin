package ru.benos.gofindwin.client

import net.fabricmc.loader.api.FabricLoader
import ru.benos.gofindwin.GoFindWinConst
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists

object GoFindWinClientConfig {
    data class ProfileFile(val pName: String, val pFileName: String)

    var effectsProfiles: List<ProfileFile> = listOf(
        ProfileFile("default", "effects-default.json")
    )
        private set

    var scrollProfiles: List<ProfileFile> = listOf(
        ProfileFile("default", "scroll-default.json")
    )
        private set

    fun init() {
        val root = FabricLoader.getInstance()
            .configDir
            .resolve(MOD_ID)

        val cacheDir = root.resolve(GoFindWinConst.Client.DIRECTORY_CACHE)
        val profiles = root.resolve(GoFindWinConst.Client.DIRECTORY_PROFILES)
        val client = root.resolve(GoFindWinConst.Client.CONFIG_FILE_CLIENT)

        root.createDirectories()
        cacheDir.createDirectories()
        profiles.createDirectories()

        if (!client.exists()) {
            client.createFile()
            File(client.toUri()).writeText("{}")
        }

        GoFindWinConst.CONFIG_PATH = root
    }

    fun pushCache(f: String, str: String) {
        val file = File(
            GoFindWinConst.CONFIG_PATH
                .resolve(GoFindWinConst.Client.DIRECTORY_CACHE)
                .resolve(f)
                .toUri()
        )

        file.parentFile?.mkdirs()
        file.writeText(str)
    }

    fun pullCache(f: String): String? {
        val file = File(
            GoFindWinConst.CONFIG_PATH
                .resolve(GoFindWinConst.Client.DIRECTORY_CACHE)
                .resolve(f)
                .toUri()
        )

        if (!file.exists()) return null

        return file.readText()
    }

    fun pushProfile(f: String, str: String) {
        val file = File(
            GoFindWinConst.CONFIG_PATH
                .resolve(GoFindWinConst.Client.DIRECTORY_PROFILES)
                .resolve(f)
                .toUri()
        )

        file.parentFile?.mkdirs()
        file.writeText(str)
    }

    fun pullProfile(f: String): String? {
        val file = File(
            GoFindWinConst.CONFIG_PATH
                .resolve(GoFindWinConst.Client.DIRECTORY_PROFILES)
                .resolve(f)
                .toUri()
        )

        if (!file.exists()) return null

        return file.readText()
    }
}