package ru.benos.gofindwin

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GoFindWin : ModInitializer {
    val LOGGER: Logger = LoggerFactory.getLogger(GoFindWinConst.MOD_NAME)

    private val CACHE: MutableMap<String, Any> = mutableMapOf()

    override fun onInitialize() {
        LOGGER.info("Initialization...")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> cache(value: T): T {
        val key = value.toString()

        if (!CACHE.containsKey(key))
            CACHE[key] = value

        return CACHE[key] as T
    }

    fun cleanupCache(key: String?) {
        if (key == null)
            CACHE.clear()
        else
            CACHE.remove(key)
    }
}
