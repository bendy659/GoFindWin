package ru.benos_codex.gofindwin.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import ru.benos_codex.gofindwin.hud.RunTimerHudPayload

object GoFindWinNetworking {
    fun initialize() {
        PayloadTypeRegistry.playS2C().register(RunTimerHudPayload.TYPE, RunTimerHudPayload.STREAM_CODEC)
    }
}
