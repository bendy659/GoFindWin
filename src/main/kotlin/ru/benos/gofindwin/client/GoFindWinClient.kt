package ru.benos.gofindwin.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.benos.gofindwin.GoFindWinConst
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.gofindwin.GoFindWinConst.k
import ru.benos.gofindwin.GoFindWinConst.mident
import ru.benos.gofindwin.GoFindWinConst.style
import ru.benos.gofindwin.client.gui.SettingsScreen
import ru.benos.gofindwin.client.hud.effects.ParticleManager
import ru.benos_codex.client.gui.auto_layout.DemoAutoLayoutScreen

object GoFindWinClient : ClientModInitializer {
    val LOGGER: Logger = LoggerFactory.getLogger("${GoFindWinConst.MOD_NAME} | Client")

    object Keybinds {
        data class Keybind(val map: KeyMapping, val onClick: () -> Unit)

        private val listeners: MutableList<Keybind> = mutableListOf()

        val category = KeyMapping.Category
            .register("keybinds".mident)

        var TEST_SPAWN : KeyMapping = keybind("tess_spawn", GLFW.GLFW_KEY_R, ParticleManager::test)
        var DEMO_SCREEN: KeyMapping = keybind("demo_screen", GLFW.GLFW_KEY_UNKNOWN, DemoAutoLayoutScreen::show)

        var SETTINGS_SCREEN: KeyMapping = keybind("settings_screen", GLFW.GLFW_KEY_O, SettingsScreen::show)

        val init: Unit get() =
            ClientTickEvents.END_CLIENT_TICK.register { _ ->
                listeners.forEach { if (it.map.consumeClick()) it.onClick() }
            }

        private fun keybind(id: String, key: Int, onClick: () -> Unit): KeyMapping {
            listeners.addLast(
                Keybind(
                    map = KeyBindingHelper.registerKeyBinding(
                        KeyMapping(
                            id,
                            InputConstants.Type.KEYSYM,
                            key,
                            category
                        )
                    ),
                    onClick = onClick
                )
            )

            return listeners.last().map
        }
    }

    object Translate {
        private val cache: MutableMap<String, Component> = mutableMapOf()

        fun reload(manager: ResourceManager) {
            val componentStyleKeys = setOf("text", "color", "bold", "italic", "underlined")

            @Suppress("UNCHECKED_CAST")
            fun jsonObjectToComponent(obj: JsonObject, key: String): Component {
                fun <T> JsonObject.get(key: String, default: T): T {
                    if (!this@get.has(key)) return default
                    val value = this@get[key]

                    if (!value.isJsonPrimitive) return default

                    return when (default) {
                        is String  -> value.asString as T
                        is Boolean -> value.asBoolean as T
                        else -> default
                    }
                }

                fun String.toColorInt(): Int =
                    ChatFormatting.getByName(this@toColorInt)?.color ?: ChatFormatting.WHITE.color!!

                return obj.get("text", key)
                    .k
                    .style(
                        color = obj.get("color", "white").toColorInt(),
                        bold = obj.get("bold", false),
                        italic = obj.get("italic", false),
                        underlined = obj.get("underlined", false)
                    )
            }

            fun JsonObject.isComponentObject(): Boolean =
                keySet().isNotEmpty() && keySet().all(componentStyleKeys::contains)

            fun jsonArrayToComponent(arr: JsonArray, key: String): Component {
                val mutable = Component.empty()

                arr.forEach { element ->
                    when {
                        element.isJsonPrimitive ->
                            mutable.append(element.asJsonPrimitive.asString.k)
                        element.isJsonObject ->
                            mutable.append(jsonObjectToComponent(element.asJsonObject, key))
                        element.isJsonArray ->
                            mutable.append(jsonArrayToComponent(element.asJsonArray, key))
                    }
                }

                return mutable
            }

            fun cacheValue(path: String, value: com.google.gson.JsonElement) {
                when {
                    value.isJsonPrimitive ->
                        cache[path] = value.asJsonPrimitive.asString.k
                    value.isJsonArray ->
                        cache[path] = jsonArrayToComponent(value.asJsonArray, path)
                    value.isJsonObject -> {
                        val obj = value.asJsonObject

                        if (obj.isComponentObject()) {
                            cache[path] = jsonObjectToComponent(obj, path)
                            return
                        }

                        obj.asMap().forEach { (childKey, childValue) ->
                            val childPath = if (path.isEmpty()) childKey else "$path.$childKey"
                            cacheValue(childPath, childValue)
                        }
                    }
                }
            }

            cache.clear()

            val langCode = Minecraft.getInstance().languageManager.selected

            val ids = manager.listResourceStacks("lang") { id ->
                id.namespace == MOD_ID && id.path.endsWith("$langCode.obj.json")
            }.keys.toList()

            ids.forEach { id ->
                val langFile = manager.getResource(id).get()
                val str = langFile.openAsReader().readText()

                val json = JsonParser.parseString(str).asJsonObject
                json.asMap().forEach { (key, value) -> cacheValue(key, value) }
            }
        }

        val String.mtranslate: Component get() =
            cache[this@mtranslate] ?: this@mtranslate.k
    }

    object Reload: SimpleSynchronousResourceReloadListener {
        override fun getFabricId(): Identifier =
            "resources".mident

        override fun onResourceManagerReload(resourceManager: ResourceManager) {
            Translate.reload(resourceManager)
        }
    }

    override fun onInitializeClient() {
        LOGGER.info("Initialization...")

        ResourceManagerHelperImpl.get(PackType.CLIENT_RESOURCES).registerReloadListener(Reload)

        Keybinds.init
        ParticleManager.init

        HudElementRegistry.addFirst("particle".mident) { guiGraphics, deltaTracker ->
            ParticleManager.draw(guiGraphics, deltaTracker)
        }
    }
}
