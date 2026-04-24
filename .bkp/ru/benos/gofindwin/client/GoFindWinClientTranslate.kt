package ru.benos.gofindwin.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.packs.resources.ResourceManager
import ru.benos.gofindwin.GoFindWinConst.Client.literal
import ru.benos.gofindwin.GoFindWinConst.Client.style
import ru.benos.gofindwin.GoFindWinConst.MOD_ID

object GoFindWinClientTranslate {
    private val cache: MutableMap<String, JsonElement> = mutableMapOf()

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
            json.asMap().forEach { (key, value) ->
                cacheValue(componentStyleKeys, key, value)
            }
        }
    }

    val String.mtranslate: MutableComponent
        get() = this@mtranslate.mtranslate()

    fun String.mtranslate(vararg args: Any): MutableComponent {
        val state = PlaceholderState()
        val value = cache[this@mtranslate]

        return when {
            value == null ->
                replacePlaceholders(this@mtranslate, args, state).literal.copy()
            else ->
                jsonElementToComponent(value, this@mtranslate, args, state).copy()
        }
    }

    private fun JsonObject.isComponentObject(set: Set<String>): Boolean =
        keySet().isNotEmpty() && keySet().all(set::contains)

    private fun jsonArrayToComponent(arr: JsonArray, key: String, args: Array<out Any>, state: PlaceholderState): Component {
        val mutable = Component.empty()

        arr.forEach { element ->
            when {
                element.isJsonPrimitive ->
                    mutable.append(replacePlaceholders(element.asJsonPrimitive.asString, args, state).literal)
                element.isJsonObject ->
                    mutable.append(jsonObjectToComponent(element.asJsonObject, key, args, state))
                element.isJsonArray ->
                    mutable.append(jsonArrayToComponent(element.asJsonArray, key, args, state))
            }
        }

        return mutable
    }

    private fun cacheValue(set: Set<String>, path: String, value: JsonElement) {
        when {
            value.isJsonPrimitive ->
                cache[path] = value.deepCopy()
            value.isJsonArray ->
                cache[path] = value.deepCopy()
            value.isJsonObject -> {
                val obj = value.asJsonObject

                if (obj.isComponentObject(set)) {
                    cache[path] = value.deepCopy()
                    return
                }

                obj.asMap().forEach { (childKey, childValue) ->
                    val childPath = if (path.isEmpty()) childKey else "$path.$childKey"
                    cacheValue(set, childPath, childValue)
                }
            }
        }
    }

    private fun jsonElementToComponent(
        value: JsonElement,
        key: String,
        args: Array<out Any>,
        state: PlaceholderState
    ): Component = when {
        value.isJsonPrimitive ->
            replacePlaceholders(value.asJsonPrimitive.asString, args, state).literal
        value.isJsonArray ->
            jsonArrayToComponent(value.asJsonArray, key, args, state)
        value.isJsonObject ->
            jsonObjectToComponent(value.asJsonObject, key, args, state)
        else ->
            key.literal
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonObjectToComponent(
        obj: JsonObject,
        key: String,
        args: Array<out Any>,
        state: PlaceholderState
    ): Component = replacePlaceholders(obj.get("text", key), args, state)
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
            is String -> value.asString as T
            is Boolean -> value.asBoolean as T
            else -> default
        }
    }

    private class PlaceholderState(var nextIndex: Int = 0)

    private fun replacePlaceholders(template: String, args: Array<out Any>, state: PlaceholderState): String {
        if (args.isEmpty() || !template.contains('%')) return template

        val out = StringBuilder(template.length + args.size * 4)
        var i = 0

        while (i < template.length) {
            val ch = template[i]

            if (ch != '%') {
                out.append(ch)
                i++
                continue
            }

            if (i + 1 < template.length && template[i + 1] == '%') {
                out.append('%')
                i += 2
                continue
            }

            var j = i + 1
            var explicitIndex: Int? = null

            while (j < template.length && template[j].isDigit()) {
                j++
            }

            if (j > i + 1 && j < template.length && template[j] == '$') {
                val indexToken = template.substring(i + 1, j)
                explicitIndex = indexToken.toIntOrNull()?.minus(1)
                j++
            } else {
                j = i + 1
            }

            if (j < template.length && template[j] == 's') {
                val argIndex = explicitIndex ?: state.nextIndex++
                val replacement = args.getOrNull(argIndex)?.toString() ?: "%s"
                out.append(replacement)
                i = j + 1
                continue
            }

            out.append('%')
            i++
        }

        return out.toString()
    }

    private fun String.toColorInt(): Int =
        ChatFormatting.getByName(this@toColorInt)?.color ?: ChatFormatting.WHITE.color!!
}
