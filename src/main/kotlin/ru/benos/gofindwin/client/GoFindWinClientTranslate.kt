package ru.benos.gofindwin.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.resources.ResourceManager
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.GoFindWinConst.literal
import ru.benos.gofindwin.GoFindWinConst.style

object GoFindWinClientTranslate {
    private val cache: MutableMap<String, Component> = mutableMapOf()

    private val componentStyleKeys = setOf("text", "color", "bold", "italic", "underlined")

    fun reload(manager: ResourceManager) {
        cache.clear()

        val langCode = Minecraft.getInstance().languageManager.selected
        val ids = manager.listResourceStacks("lang") { id ->
            id.namespace == MOD_ID && id.path.endsWith("$langCode.obj.json")
        }.keys.toList()

        ids.forEach { id ->
            val langFile = manager.getResource(id).get()
            val str = langFile.openAsReader().readText()

            val json = JsonParser.parseString(str).asJsonObject
            json.asMap().forEach { (key, value) -> cacheValue(componentStyleKeys, key, value) }
        }
    }

    val String.mtranslate: Component get() =
        cache[this@mtranslate] ?: this@mtranslate.literal

    private fun JsonObject.isComponentObject(set: Set<String>): Boolean =
        keySet().isNotEmpty() && keySet().all(set::contains)

    private fun jsonArrayToComponent(arr: JsonArray, key: String): Component {
        val mutable = Component.empty()

        arr.forEach { element ->
            when {
                element.isJsonPrimitive ->
                    mutable.append(element.asJsonPrimitive.asString.literal)
                element.isJsonObject ->
                    mutable.append(jsonObjectToComponent(element.asJsonObject, key))
                element.isJsonArray ->
                    mutable.append(jsonArrayToComponent(element.asJsonArray, key))
            }
        }

        return mutable
    }

    private fun cacheValue(set: Set<String>, path: String, value: JsonElement) {
        when {
            value.isJsonPrimitive ->
                cache[path] = value.asJsonPrimitive.asString.literal
            value.isJsonArray ->
                cache[path] = jsonArrayToComponent(value.asJsonArray, path)
            value.isJsonObject -> {
                val obj = value.asJsonObject

                if (obj.isComponentObject(set)) {
                    cache[path] = jsonObjectToComponent(obj, path)
                    return
                }

                obj.asMap().forEach { (childKey, childValue) ->
                    val childPath = if (path.isEmpty()) childKey else "$path.$childKey"
                    cacheValue(set, childPath, childValue)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToComponent(obj: JsonObject, key: String): Component =
        obj.get("text", key)
            .literal
            .style(
                color = obj.get("color", "white").toColorInt(),
                bold = obj.get("bold", false),
                italic = obj.get("italic", false),
                underlined = obj.get("underlined", false)
            )

    @Suppress("UNCHECKED_CAST")
    private fun <T> JsonObject.get(key: String, default: T): T {
        if (!this@get.has(key)) return default
        val value = this@get[key]

        if (!value.isJsonPrimitive) return default

        return when (default) {
            is String, Boolean -> value.asString as T
            else -> default
        }
    }

    private fun String.toColorInt(): Int =
        ChatFormatting.getByName(this@toColorInt)?.color ?: ChatFormatting.WHITE.color!!
}