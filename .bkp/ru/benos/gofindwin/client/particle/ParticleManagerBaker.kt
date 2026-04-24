package ru.benos.gofindwin.client.particle

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import ru.benos.gofindwin.client.GoFindWinClientConfig
import ru.benos.gofindwin.client.data.ExpressionBakeData
import ru.benos.gofindwin.client.data.FileType
import ru.benos_codex.client.particle.ParticleBakePipeline

@Environment(EnvType.CLIENT)
object ParticleManagerBaker {
    enum class SourceType {
        PARTICLE_COUNT, PARTICLE_PER_MIN, PARTICLE_LIFETIME,
        ITEM, TEXTURE, BLOCK, ENTITY, LABEL, COLOR,
        POSITION, ROTATION, SCALE
    }

    var currentBake: List<List<ExpressionBakeData?>> = emptyList()
        private set

    private var bakedCache: List<List<List<ExpressionBakeData?>>> = emptyList()

    var bakingCurrentBakeIndex: Int = -1
        private set
    var bakingTotalBakeIndex: Int = -1
        private set
    var bakingCurrentTick: Int = -1
        private set
    var bakingTotalTicks: Int = -1
        private set

    fun saveCache() {
        GoFindWinClientConfig.push(FileType.CACHE_EFFECTS, bakedCache)
    }

    fun loadCache() {
        bakedCache = runCatching {
            GoFindWinClientConfig.pull<List<List<List<ExpressionBakeData?>>>>(FileType.CACHE_EFFECTS)
        }.getOrDefault(emptyList())

        currentBake = bakedCache.firstOrNull().orEmpty()
    }

    fun baking(iterations: Int, vararg data: Map<SourceType, String>) {
        cleanup()

        bakingTotalBakeIndex = data.size

        repeat(iterations) {
            bakedCache = data.mapIndexed { bakeIndex, source ->
                bakingCurrentBakeIndex = bakeIndex + 1

                ParticleBakePipeline.bake(source) { current, total ->
                    bakingCurrentTick = current
                    bakingTotalTicks = total
                }
            }
        }

        saveCache()
        loadCache()
    }

    fun cleanup() {
        bakedCache = emptyList()
        currentBake = emptyList()

        bakingCurrentBakeIndex = -1
        bakingTotalBakeIndex = -1
        bakingCurrentTick = -1
        bakingTotalTicks = -1
    }
}
