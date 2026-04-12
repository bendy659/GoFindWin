package ru.benos.gofindwin.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import ru.benos.gofindwin.GoFindWinConst.mident
import ru.benos.gofindwin.client.gui.SettingsScreen
import ru.benos_codex.client.gui.auto_layout.DemoAutoLayoutScreen

object GoFindWinClientKeybind {
    data class Keybind(val map: KeyMapping, val onClick: () -> Unit)

    val category = KeyMapping.Category.register("keybinds".mident)

    private val listeners: MutableList<Keybind> = mutableListOf()

    var TEST_SPAWN : KeyMapping = keybind("tess_spawn", GLFW.GLFW_KEY_R, ParticleManager::test)
    var DEMO_SCREEN: KeyMapping = keybind("demo_screen", GLFW.GLFW_KEY_UNKNOWN, DemoAutoLayoutScreen::show)

    var SETTINGS_SCREEN: KeyMapping = keybind("settings_screen", GLFW.GLFW_KEY_O, SettingsScreen::show)

    fun init() =
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            listeners.forEach { if (it.map.consumeClick()) it.onClick() }
        }

    private fun keybind(id: String, key: Int, onClick: () -> Unit): KeyMapping {
        val keyMapping = KeyMapping(id, InputConstants.Type.KEYSYM, key, category)
        val keyMap = KeyBindingHelper.registerKeyBinding(keyMapping)
        val keybind = Keybind(keyMap, onClick)

        listeners.addLast(keybind)

        return listeners.last().map
    }
}