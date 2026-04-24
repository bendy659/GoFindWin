package ru.benos_codex.client.particle

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import ru.benos.gofindwin.GoFindWinConst.ExpressionVariables
import ru.benos.gofindwin.client.GoFindWinClient
import ru.benos.gofindwin.client.particle.ParticleManagerBaker
import ru.benos.gofindwin.client.data.Color
import ru.benos.gofindwin.client.data.ExpressionBakeData
import ru.benos.gofindwin.client.data.ParticleInstance
import ru.benos.gofindwin.client.data.ParticleItemData
import ru.benos.gofindwin.client.data.ParticleRenderMode
import ru.benos.gofindwin.client.data.Vec2
import ru.benos.gofindwin.client.data.Vec3
import ru.benos_codex.expression_engine.CompiledExpression
import ru.benos_codex.expression_engine.ExpressionContextBuilder
import kotlin.math.max

@Environment(EnvType.CLIENT)
object ParticleBakePipeline {
    fun bake(
        source: Map<ParticleManagerBaker.SourceType, String>,
        onTickBaked: (currentTick: Int, totalTicks: Int) -> Unit = { _, _ -> }
    ): List<List<ExpressionBakeData?>> {
        val compiled = CompiledSources(source)
        val particleCount = evaluateParticleCount(compiled)
        if (particleCount <= 0) return emptyList()

        val particles = buildParticles(particleCount, compiled)
        val totalTicks = particles.maxOfOrNull { it.instance.lifetime } ?: 0
        if (totalTicks <= 0) return emptyList()

        val frames = List(totalTicks) {
            MutableList<ExpressionBakeData?>(particleCount) { null }
        }

        particles.forEach { bakedParticle ->
            val particle = bakedParticle.instance
            val item = resolveItem(source[ParticleManagerBaker.SourceType.ITEM])

            for (tick in 0 until particle.lifetime) {
                particle.age = tick
                applyFrameState(compiled, particle, bakedParticle.cache)

                frames[tick][particle.index] = ExpressionBakeData(
                    particleCount = particleCount,
                    particlePerMin = 0,
                    particleLifetime = particle.lifetime,
                    item = item,
                    texture = null,
                    block = null,
                    entity = null,
                    label = null,
                    color = particle.color.int,
                    position = particle.position.toMcVec2,
                    rotation = particle.rotation.toMcVec3,
                    scale = particle.scale.toMcVec3
                )
            }
        }

        frames.indices.forEach { tick ->
            onTickBaked(tick + 1, totalTicks)
        }

        return frames.map { it.toList() }
    }

    private fun evaluateParticleCount(compiled: CompiledSources): Int =
        compiled.particleCount?.evaluate(
            GoFindWinClient.EXPRESSION_ENGINE,
            buildContext(null)
        )?.asDouble()?.toInt() ?: 1

    private fun buildParticles(count: Int, compiled: CompiledSources): List<BakedParticle> =
        List(count) { index ->
            val cache = linkedMapOf<String, Any>()
            val particle = ParticleInstance(
                index = index,
                lifetime = 1,
                spawnPosition = Vec2(0f, 0f),
                position = Vec2(0f, 0f),
                rotation = Vec3(0f, 0f, 0f),
                scale = Vec3(16f, 16f, 1f),
                oldPosition = Vec2(0f, 0f),
                oldRotation = Vec3(0f, 0f, 0f),
                oldScale = Vec3(16f, 16f, 1f),
                oldColor = Color(255, 255, 255, 255)
            )

            val lifetime = compiled.particleLifetime?.evaluate(
                GoFindWinClient.EXPRESSION_ENGINE,
                buildContext(particle, cache)
            )?.asDouble()?.toInt() ?: 20

            particle.lifetime = max(1, lifetime)
            particle.item = ParticleItemData.Example
            BakedParticle(particle, cache)
        }

    private fun applyFrameState(
        compiled: CompiledSources,
        particle: ParticleInstance,
        cache: MutableMap<String, Any>
    ) {
        val context = buildContext(particle, cache)

        compiled.position?.evaluate(GoFindWinClient.EXPRESSION_ENGINE, context)
            ?.asVec2()
            ?.let { particle.updatePosition(it.x, it.y) }

        compiled.rotation?.evaluate(GoFindWinClient.EXPRESSION_ENGINE, context)
            ?.asVec3()
            ?.let { particle.updateRotation(it.x, it.y, it.z) }

        compiled.scale?.evaluate(GoFindWinClient.EXPRESSION_ENGINE, context)
            ?.asVec3()
            ?.let { particle.updateScale(it.x, it.y, it.z) }
    }

    private fun buildContext(
        particle: ParticleInstance?,
        cache: MutableMap<String, Any>? = null
    ) = ExpressionContextBuilder().apply {
        cache?.let(::applyCache)

        if (particle == null) {
            number(ExpressionVariables.ParticlesCount.k, 0)
            return@apply
        }

        number(ExpressionVariables.Particle.Index.k, particle.index)
        number(ExpressionVariables.Particle.Life.Age.k, particle.age)
        number(ExpressionVariables.Particle.Life.Max.k, particle.lifetime)
        number(ExpressionVariables.Particle.Life.Factor.k, particle.lifeFactor)
        number(ExpressionVariables.Particle.Position.X.k, particle.position.x)
        number(ExpressionVariables.Particle.Position.Y.k, particle.position.y)
        number(ExpressionVariables.Particle.SpawnPosition.X.k, particle.spawnPosition?.x ?: particle.position.x)
        number(ExpressionVariables.Particle.SpawnPosition.Y.k, particle.spawnPosition?.y ?: particle.position.y)
        number(ExpressionVariables.Particle.Rotation.X.k, particle.rotation.x)
        number(ExpressionVariables.Particle.Rotation.Y.k, particle.rotation.y)
        number(ExpressionVariables.Particle.Rotation.Z.k, particle.rotation.z)
        number(ExpressionVariables.Particle.Scale.X.k, particle.scale.x)
        number(ExpressionVariables.Particle.Scale.Y.k, particle.scale.y)
        number(ExpressionVariables.Particle.Scale.Z.k, particle.scale.z)
        number(ExpressionVariables.Particle.Color.R.k, particle.color.r)
        number(ExpressionVariables.Particle.Color.G.k, particle.color.g)
        number(ExpressionVariables.Particle.Color.B.k, particle.color.b)
        number(ExpressionVariables.Particle.Color.A.k, particle.color.a)
    }.build()

    private fun resolveItem(raw: String?): ParticleItemData =
        raw?.let(::parseItem)?.copy() ?: ParticleItemData.Example.copy()

    private fun parseItem(raw: String): ParticleItemData? {
        val normalized = raw.trim()
        val match = ITEM_REGEX.matchEntire(normalized) ?: return null
        val itemId = match.groupValues[1]
        val renderMode = match.groupValues.getOrNull(2)
            ?.takeIf(String::isNotBlank)
            ?.let(::resolveRenderMode)

        return ParticleItemData(
            id = itemId,
            particleRenderMode = renderMode ?: ParticleRenderMode.VOLUME
        )
    }

    private fun resolveRenderMode(id: String): ParticleRenderMode? =
        ParticleRenderMode.entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

    private data class CompiledSources(
        val particleCount: CompiledExpression?,
        val particleLifetime: CompiledExpression?,
        val position: CompiledExpression?,
        val rotation: CompiledExpression?,
        val scale: CompiledExpression?
    ) {
        constructor(source: Map<ParticleManagerBaker.SourceType, String>) : this(
            particleCount = source[ParticleManagerBaker.SourceType.PARTICLE_COUNT]?.compile(),
            particleLifetime = source[ParticleManagerBaker.SourceType.PARTICLE_LIFETIME]?.compile(),
            position = source[ParticleManagerBaker.SourceType.POSITION]?.compile(),
            rotation = source[ParticleManagerBaker.SourceType.ROTATION]?.compile(),
            scale = source[ParticleManagerBaker.SourceType.SCALE]?.compile()
        )
    }

    private data class BakedParticle(
        val instance: ParticleInstance,
        val cache: MutableMap<String, Any>
    )

    private fun String.compile(): CompiledExpression =
        GoFindWinClient.EXPRESSION_ENGINE.compile(this)

    private val ITEM_REGEX = Regex("""item\(([^,\s]+)(?:,\s*([^)]+))?\)""")
}
