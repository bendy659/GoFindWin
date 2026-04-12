package ru.benos.gofindwin.client

import com.google.gson.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.benos.gofindwin.GoFindWinConst.Client.literal
import ru.benos.gofindwin.client.data.ExpressionBakeData
import ru.benos.gofindwin.client.data.ItemRenderMode
import ru.benos.gofindwin.client.data.ParticleItemData
import ru.benos.gofindwin.client.data.ParticleTextureData
import ru.benos_codex.client.ExpressionContextBuilder
import ru.benos.gofindwin.client.data.ParticleInstance

@Environment(EnvType.CLIENT)
object ParticleManager {
    enum class SourceType {
        PARTICLE_COUNT, PARTICLE_PER_MIN, PARTICLE_LIFETIME,
        ITEM, TEXTURE, TEXT,
        COLOR,
        POSITION, ROTATION, SCALE
    }

    const val EFFECTS_CACHE: String = "effects_cache.json"

    private val particles : MutableList<ParticleInstance> = mutableListOf() // particle-index -> particle-instance
    private var bakedCache: List<List<List<ExpressionBakeData>>> = emptyList() // bake-index -> tick -> particle-index -> data

    var bakingCurrentBakeIndex: Int = -1
    var bakingTotalBakeIndex  : Int = -1
    var bakingCurrentTick     : Int = -1
    var bakingTotalTicks      : Int = -1

    val init: Unit get() =
        ClientTickEvents.END_CLIENT_TICK.register { tick() }

    fun saveCache() {
        val gson = Gson().toJson(bakedCache)
        GoFindWinClientConfig.pushCache(EFFECTS_CACHE, gson)
    }

    fun loadCache() {
        val str = GoFindWinClientConfig.pullCache(EFFECTS_CACHE)
        str ?: return

        val json = runCatching { JsonParser.parseString(str).asJsonArray }.getOrNull() ?: return
        val mutable = mutableListOf<List<List<ExpressionBakeData>>>()

        json.forEach { bakeIndexElement ->
            val bakeTicks = bakeIndexElement.asJsonArrayOrNull() ?: return@forEach
            val bakeIndexMutable = mutableListOf<List<ExpressionBakeData>>()

            bakeTicks.forEach { tickElement ->
                val tickEntries = tickElement.asJsonArrayOrNull() ?: return@forEach
                val tickMutable = mutableListOf<ExpressionBakeData>()

                tickEntries.forEach { bakeDataElement ->
                    val data = bakeDataElement.asJsonObjectOrNull() ?: return@forEach

                    val baked = ExpressionBakeData.Companion.Builder()
                        .particleCount(*data.intList("particleCount").toIntArray())
                        .particlePerMin(*data.intList("particlePerMin").toIntArray())
                        .particleLifetime(*data.intList("particleLifetime").toIntArray())
                        .item(*data.itemList("item").toTypedArray())
                        .texture(*data.textureList("texture").toTypedArray())
                        .label(*data.labelList("label").toTypedArray())
                        .color(*data.intList("color").toIntArray())
                        .position(*data.vec2List("position").toTypedArray())
                        .rotation(*data.vec3List("rotation").toTypedArray())
                        .scale(*data.vec3List("scale").toTypedArray())
                        .build()

                    tickMutable += baked
                }

                bakeIndexMutable += tickMutable
            }

            mutable += bakeIndexMutable
        }

        bakedCache = mutable
    }

    fun baking(vararg data: Map<SourceType, String>) { // data -> bake-data -> (type, raw)
        GoFindWinClient.LOGGER.info("Start baking...")

        // cleanup cache //
        bakedCache = emptyList()

        // Create link to global Expression engine //
        val engine = GoFindWinClient.EXPRESSION_ENGINE

        // baked data //
        val bakeData = mutableListOf<List<List<ExpressionBakeData>>>() // bake-index -> tick -> particle-index -> data

        // Set progress bar info //
        bakingTotalBakeIndex = data.size

        // Step 1: walking of 'data' //
        data.forEachIndexed { bakeIndex, particlesInstance ->
            // Set progressbar info //
            bakingCurrentBakeIndex = bakeIndex

            // Step 2: Create new temporality particle instances //

            // Create new massive //
            val instances = MutableList<ParticleInstance?>(particlesInstance.size) { instanceIndex ->
                // Get lifetime prop from instance (or set default value 0)
                val lifeTimeDat = particlesInstance
                    .getOrDefault(SourceType.PARTICLE_LIFETIME, "0")
                // Compile lifetime value to Int //
                val lifetime = engine.compile(lifeTimeDat)
                    .evaluate(engine)
                    .asDouble()
                    .toInt()

                // Write new particle instance //
                ParticleInstance(instanceIndex, lifetime)
            }

            // Step 3: Ticking new particles //
            var tick = 0 // tick of ticking //

            val tickData = mutableListOf<List<ExpressionBakeData>>() // tick -> particle-index -> data

            // get raw-datas //
            val posRaw = particlesInstance.getOrDefault(SourceType.POSITION, "vec2(0.0, 0.0)")
            val rotRaw = particlesInstance.getOrDefault(SourceType.ROTATION, "vec3(0.0, 0.0, 0.0)")
            val sclRaw = particlesInstance.getOrDefault(SourceType.SCALE, "vec3(0.0, 0.0, 0.0)")

            // Compile values //
            val posCompile = engine.compile(posRaw)
            val rotCompile = engine.compile(rotRaw)
            val sclCompile = engine.compile(sclRaw)

            // Ticking //
            while (instances.any { it != null }) {
                // Set progressbar info //
                bakingCurrentTick = tick
                if (bakingCurrentTick > bakingTotalTicks)
                    bakingTotalTicks = bakingCurrentTick

                val instancesData = mutableListOf<ExpressionBakeData>() // particle-index -> data

                instances.forEachIndexed { instanceIndex, instance ->
                    if (instance == null) return@forEachIndexed

                    // Create snapshot expression context //
                    val ctx = ExpressionContextBuilder().apply {
                        number("particle.position.x", instance.position.x)
                        number("particle.position.y", instance.position.y)

                        number("particle.rotation.x", instance.rotation.x)
                        number("particle.rotation.y", instance.rotation.y)
                        number("particle.rotation.z", instance.rotation.z)

                        number("particle.scale.x", instance.scale.x)
                        number("particle.scale.y", instance.scale.y)
                        number("particle.scale.z", instance.scale.z)
                    }.build()

                    // Evaluate new values //
                    val pos = posCompile.evaluate(engine, ctx).asVec2()
                    val rot = rotCompile.evaluate(engine, ctx).asVec3()
                    val scl = sclCompile.evaluate(engine, ctx).asVec3()

                    // update instance values //
                    instance.updatePosition(pos.x, pos.y)
                    instance.updateRotation(rot.x, rot.y, rot.z)
                    instance.updateScale(scl.x, scl.y, scl.z)

                    // update instance age //
                    instance.age = tick

                    if (instance.lifeFactor > 1.0f) {
                        instances[instanceIndex] = null
                        return@forEachIndexed
                    }

                    // create new baked data //
                    val bake = ExpressionBakeData.Companion.Builder()
                        .position(instance.position.toMcVec2)
                        .rotation(instance.rotation.toMcVec3)
                        .scale(instance.scale.toMcVec3)

                    // Push baked data //
                    instancesData.add(instanceIndex, bake.build())
                }

                // Push tick data //
                tickData.add(tick, instancesData)

                tick++
            }

            // Push bake data //
            bakeData.add(bakeIndex, tickData)
        }

        // Finally push to cache //
        bakedCache = bakeData

        // End baking //
        bakingCurrentBakeIndex = -1
        bakingTotalBakeIndex   = -1
        bakingCurrentTick      = -1
        bakingTotalTicks       = -1

        saveCache()

        GoFindWinClient.LOGGER.info("Baking ended.")
    }

    fun cleanup() {
        particles.clear()
    }

    fun tick() {
        if (bakedCache.isEmpty()) return


    }

    fun draw() { }

    fun test() { }

    private fun JsonElement.asJsonArrayOrNull(): JsonArray? =
        takeIf { isJsonArray }?.asJsonArray

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        takeIf { isJsonObject }?.asJsonObject

    private fun JsonObject.intList(key: String): List<Int> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element -> runCatching { element.asInt }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: when (key) {
                "particleCount" -> listOf(1)
                "particlePerMin" -> listOf(60)
                "particleLifetime" -> listOf(20)
                "color" -> listOf(0xFFFFFFFF.toInt())
                else -> emptyList()
            }

    private fun JsonObject.itemList(key: String): List<ParticleItemData> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val id = obj["id"]?.asString ?: return@mapNotNull null
                val renderMode = obj["itemRenderMode"]?.let { modeElement ->
                    runCatching { ItemRenderMode.valueOf(modeElement.asString) }.getOrNull()
                } ?: ItemRenderMode.D3
                ParticleItemData(id, renderMode)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(ParticleItemData.Example)

    private fun JsonObject.textureList(key: String): List<ParticleTextureData> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val id = obj["id"]?.asString ?: return@mapNotNull null
                val x = obj["x"]?.asFloat ?: 0f
                val y = obj["y"]?.asFloat ?: 0f
                val w = obj["w"]?.asFloat ?: 16f
                val h = obj["h"]?.asFloat ?: 16f
                ParticleTextureData(id, x, y, w, h)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(ParticleTextureData.Example)

    private fun JsonObject.labelList(key: String): List<Component> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element ->
                when {
                    element.isJsonPrimitive -> runCatching { element.asString.literal }.getOrNull()
                    element.isJsonObject -> {
                        val obj = element.asJsonObject
                        when {
                            obj.has("string") -> obj["string"].asString.literal
                            obj.has("text") -> obj["text"].asString.literal
                            else -> element.toString().literal
                        }
                    }
                    else -> null
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("GoFindWin".literal)

    private fun JsonObject.vec2List(key: String): List<Vec2> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val x = obj["x"]?.asFloat ?: 0f
                val y = obj["y"]?.asFloat ?: 0f
                Vec2(x, y)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(Vec2.ZERO)

    private fun JsonObject.vec3List(key: String): List<Vec3> =
        get(key)
            ?.asJsonArrayOrNull()
            ?.mapNotNull { element ->
                val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
                val x = obj["x"]?.asDouble ?: 0.0
                val y = obj["y"]?.asDouble ?: 0.0
                val z = obj["z"]?.asDouble ?: 0.0
                Vec3(x, y, z)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(Vec3.ZERO)
}
