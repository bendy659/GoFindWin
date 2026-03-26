package ru.benos_codex.gofindwin.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import ru.benos_codex.gofindwin.client.hud.effects.HudEffectEditorScreen
import ru.benos_codex.gofindwin.client.hud.effects.HudCelebrationConfigManager
import ru.benos_codex.gofindwin.client.hud.effects.HudCelebrationEffectsClient
import ru.benos_codex.gofindwin.client.hud.RunTimerHudRenderer
import ru.benos_codex.gofindwin.client.hud.RunTimerHudStateStore
import ru.benos_codex.gofindwin.client.hud.TimerHudConfigManager
import ru.benos_codex.gofindwin.hud.RunTimerHudPayload

class GofindwinClient : ClientModInitializer {
    private lateinit var openEffectsEditorKey: KeyMapping

    override fun onInitializeClient() {
        TimerHudConfigManager.load()
        HudCelebrationConfigManager.load()
        RunTimerHudRenderer.initialize()
        openEffectsEditorKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.gofindwin.open_effect_editor",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gofindwin", "editor"))
            )
        )

        ClientPlayNetworking.registerGlobalReceiver(RunTimerHudPayload.TYPE) { payload, context ->
            context.client().execute {
                RunTimerHudStateStore.update(payload.toState())
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            while (openEffectsEditorKey.consumeClick()) {
                client.setScreen(HudEffectEditorScreen(client.screen))
            }

            if (client.level == null) {
                RunTimerHudStateStore.clear()
                HudCelebrationEffectsClient.clear()
            }
        })
    }
}
