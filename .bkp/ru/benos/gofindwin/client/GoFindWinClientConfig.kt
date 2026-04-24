package ru.benos.gofindwin.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import ru.benos.gofindwin.GoFindWinConst
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.client.GoFindWinClientTranslate.mtranslate
import ru.benos.gofindwin.client.data.EffectProfileData
import ru.benos.gofindwin.client.data.FileType
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object GoFindWinClientConfig {
    val ROOT: Path = FabricLoader.getInstance()
        .configDir
        .resolve(MOD_ID)

    @Environment(EnvType.CLIENT)
    object Client {
        lateinit var cacheDir: Path
        lateinit var profilesDir: Path

        lateinit var clientFile: File

        fun init() {
            // Creating directories //

            // Create a root mod config directory to config/gofindwin //
            ROOT.createDirectories()

            // Create a cache directory to config/gofindwin/.cache //
            cacheDir = ROOT.resolve(GoFindWinConst.Client.DIRECTORY_CACHE)
            cacheDir.createDirectories()

            // Create a profiles directory to config/gofindwin/profiles //
            profilesDir = ROOT.resolve(GoFindWinConst.Client.DIRECTORY_PROFILES)
            profilesDir.createDirectories()

            // Creating files //

            // Create a client conf-file to config/gofindwin/client.json //
            val clientFileDir = ROOT.resolve(GoFindWinConst.Client.CONFIG_FILE_CLIENT)
            clientFile = File(clientFileDir.toUri())
            if (!clientFile.exists()) {
                clientFile.parentFile?.mkdirs()
                clientFile.createNewFile()
                clientFile.writeText("{}")
            }

            pushDefaultEffectProfile()
        }
    }

    fun push(type: FileType, str: Any) {
        // Convert string to JSON-string //
        val jsonStr = Gson().toJson(str)

        // Get file directory //
        val dir = ROOT.resolve(type.dir)
        val file = File(dir.toUri())
        file.parentFile?.mkdirs()

        // Write JSON-string to file in directory //
        file.writeText(jsonStr)
    }

    inline fun <reified T> pull(type: FileType): T {
        // Get file directory //
        val dir = ROOT.resolve(type.dir)

        // Read file content in string //
        val str = File(dir.toUri()).readText()

        // Parse string and preserve generic type information //
        val gson = Gson().fromJson<T>(str, object : TypeToken<T>() {}.type)

        return gson
    }

    fun pushDefaultEffectProfile() {
        val type = FileType.PROFILES_EFFECTS

        // Check file existing //
        val dir = ROOT.resolve(type.dir)

        val file = File(dir.toUri())
        if (!file.exists())
            push(type, listOf(EffectProfileData.DEFAULT))

        // Get current profile file content //
        val list = runCatching { pull<List<EffectProfileData>>(type) }
            .getOrElse {
                push(type, listOf(EffectProfileData.DEFAULT))
                listOf(EffectProfileData.DEFAULT)
            }

        val hasDefaultProfile = list.any {
            it.profileName == EffectProfileData.DEFAULT.profileName
        }
        if (hasDefaultProfile) return

        val mutable = list.toMutableList()
        mutable += EffectProfileData.DEFAULT

        push(FileType.PROFILES_EFFECTS, mutable.toList())
    }
}
