package ru.benos_codex.gofindwin

import net.fabricmc.api.ModInitializer
import ru.benos_codex.gofindwin.network.GoFindWinNetworking
import ru.benos_codex.gofindwin.run.RunLifecycleManager
import ru.benos_codex.gofindwin.run.RunTargetConfigManager
import ru.benos_codex.gofindwin.run.RunTimerHudSync

class Gofindwin : ModInitializer {

    override fun onInitialize() {
        RunTargetConfigManager.load()
        GoFindWinNetworking.initialize()
        RunLifecycleManager.initialize()
        RunTimerHudSync.initialize()
    }
}
