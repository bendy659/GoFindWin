package ru.benos.gofindwin

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GoFindWin : ModInitializer {
    val LOGGER: Logger = LoggerFactory.getLogger(GoFindWinConst.MOD_NAME)

    override fun onInitialize() {
        LOGGER.info("Initialization...")
    }
}
