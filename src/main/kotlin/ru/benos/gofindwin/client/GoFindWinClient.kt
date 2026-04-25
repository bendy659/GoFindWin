package ru.benos.gofindwin.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.ARGB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.benos.gofindwin.GoFindWinConst
import ru.benos.gofindwin.GoFindWinConst.MOD_ID
import ru.benos.libs.helpers.IdentifierHelper.mident

@Environment(EnvType.CLIENT)
object GoFindWinClient : ClientModInitializer {
    object IDEHighlightColors {
        var VAR              = ARGB.color(200, 140, 220)
        val GLOBAL_VARIABLES = ARGB.color(255, 196, 88)
        val LOCAL_VARIABLES  = ARGB.color(255, 149, 126)
        val FUNCTIONS        = GLOBAL_VARIABLES

        val NUMBER    = ARGB.color(54, 190, 255)
        val STRING    = ARGB.color(149, 172, 83)
        val BOOLEAN   = ARGB.color(191, 139, 209)

        val COMMENT   = ARGB.color(119, 126, 133)
    }

    val LOGGER: Logger = LoggerFactory.getLogger("${GoFindWinConst.MOD_NAME} | Client")

    val GoFindWinClientReload =
        ResourceManagerReloadListener { resourceManager: ResourceManager ->

        }

    override fun onInitializeClient() {
        LOGGER.info("Initialization...")

        Keybinds.init()

        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloader("resources".mident(MOD_ID), GoFindWinClientReload)

        HudElementRegistry.addFirst("particle".mident(MOD_ID)) { guiGraphics, deltaTracker ->

        }
    }
}
