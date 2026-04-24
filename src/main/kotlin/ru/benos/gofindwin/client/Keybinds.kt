package ru.benos.gofindwin.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import ru.benos.gofindwin.GoFindWinConst.invoke
import ru.benos.gofindwin.GoFindWinConst.mident
import ru.benos.libs.ui_layout.DemoUiLayoutScreen

object Keybinds {
    private data class Keybind(val mapping: KeyMapping, val onClick: () -> Unit)

    private val QUEUE: MutableList<Keybind> = mutableListOf()
    private val CATEGORY: KeyMapping.Category = KeyMapping.Category("keybinds".mident)

    val KEY_OpenDemoScreen: KeyMapping = screenKeybind("open_demo_screen", GLFW.GLFW_KEY_UNKNOWN, DemoUiLayoutScreen)

    fun init() {
        // Registration //
        QUEUE.forEach { (mapping, onClick) ->
            KeyBindingRegistryImpl.registerKeyBinding(mapping)

            ClientTickEvents.END_CLIENT_TICK.register { _ -> mapping.consumeClick().invoke(onClick) }
        }
    }

    private fun keybind(keybind: Keybind): KeyMapping {
        QUEUE += keybind

        return keybind.mapping
    }

    private fun keybind(id: String, key: Int, onClick: () -> Unit): KeyMapping {
        val mapping = KeyMapping(id, key, CATEGORY)
        val keybind = Keybind(mapping, onClick)

        return keybind(keybind)
    }

    private fun screenKeybind(id: String, key: Int, screen: Screen): KeyMapping {
        val mapping = KeyMapping(id, key, CATEGORY)
        val keybind = Keybind(mapping) { Minecraft.getInstance().setScreen(screen) }

        return keybind(keybind)
    }
}