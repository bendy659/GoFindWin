package ru.benos.gofindwin.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec2
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ru.benos.gofindwin.GoFindWinConst
import ru.benos.gofindwin.GoFindWinConst.mident
import ru.benos.gofindwin.client.data.Color
import ru.benos.gofindwin.client.data.Vec3
import ru.benos.gofindwin.client.particle.ParticlePlayer
import ru.benos_codex.client.gui.auto_layout.ui.UiTextHighlightRule
import ru.benos_codex.expression_engine.*

@Environment(EnvType.CLIENT)
object GoFindWinClient : ClientModInitializer {
    val LOGGER: Logger = LoggerFactory.getLogger("${GoFindWinConst.MOD_NAME} | Client")

    val randomSource: RandomSource = RandomSource.create()
    var particleManager: ParticlePlayer? = null

    data class ExpressionSignature(val strFull: String, val strHighlight: String, val strReturn: String)
    val ideVariables: MutableList<ExpressionSignature> = mutableListOf()
    val ideFunctions: MutableList<ExpressionSignature> = mutableListOf()
    var ideHighlightRule: List<UiTextHighlightRule> = emptyList()

    private fun registerBuiltinIdeVariables() {
        fun prefixesOf(path: String): List<String> {
            val parts = path.split('.')
            return buildList {
                for (index in parts.indices) {
                    add(parts.take(index + 1).joinToString("."))
                }
            }
        }

        GoFindWinConst.ExpressionVariables.ALL
            .asSequence()
            .flatMap { prefixesOf(it.k).asSequence() }
            .distinct()
            .forEach { path ->
                if (ideVariables.none { it.strFull == path }) {
                    ideVariables += ExpressionSignature(
                        strFull = path,
                        strHighlight = path,
                        strReturn = "Any"
                    )
                }
            }
    }

    fun rebuildIdeHighlightRules(): List<UiTextHighlightRule> {
        fun buildWordRule(words: List<String>, color: Int): UiTextHighlightRule? {
            val patternBody = words
                .asSequence()
                .filter { it.isNotBlank() }
                .distinct()
                .sortedByDescending { it.length }
                .map(Regex::escape)
                .joinToString("|")
            if (patternBody.isBlank()) return null
            return UiTextHighlightRule(
                pattern = Regex("\\b(?:$patternBody)\\b"),
                color = color
            )
        }
        fun buildRegexRule(pattern: String, color: Int): UiTextHighlightRule =
            UiTextHighlightRule(
                pattern = Regex(pattern),
                color = color
            )
        fun MutableList<UiTextHighlightRule>.addRule(rule: UiTextHighlightRule?) {
            rule?.let(::add)
        }

        ideHighlightRule = buildList {
            addRule(
                buildWordRule(
                    words = ideVariables.map { it.strHighlight },
                    color = GoFindWinConst.Client.IDEHighlightColors.VARIABLES
                )
            )
            addRule(
                buildWordRule(
                    words = ideFunctions.map { it.strHighlight },
                    color = GoFindWinConst.Client.IDEHighlightColors.FUNCTIONS
                )
            )
            addRule(
                buildWordRule(
                    words = listOf("var"),
                    color = GoFindWinConst.Client.IDEHighlightColors.VAR
                )
            )
            addRule(
                buildWordRule(
                    words = listOf("false", "true"),
                    color = GoFindWinConst.Client.IDEHighlightColors.BOOLEAN
                )
            )
            addRule(
                buildRegexRule(
                    pattern = "\"(?:\\\\.|[^\"\\\\])*\"",
                    color = GoFindWinConst.Client.IDEHighlightColors.STRING
                )
            )
            addRule(
                buildRegexRule(
                    pattern = "(?<![A-Za-z_])[-+]?(?:\\d[\\d_]*)(?:\\.\\d[\\d_]*)?(?![A-Za-z_])",
                    color = GoFindWinConst.Client.IDEHighlightColors.NUMBER
                )
            )
            addRule(
                buildRegexRule(
                    pattern = "#.*",
                    color = GoFindWinConst.Client.IDEHighlightColors.COMMENT
                )
            )
        }
        return ideHighlightRule
    }

    var EXPRESSION_ENGINE: ExpressionEngine = ExpressionEngine().apply {
        fun variable(str: String, handler: (ExpressionContext) -> Any) {
            if (ideVariables.none { it.strFull == str }) {
                ideVariables += ExpressionSignature(
                    strFull = str,
                    strHighlight = str,
                    strReturn = "Any"
                )
            }
            addVariable(str) { ctx ->
                val handler = handler(ctx)

                when (handler) {
                    is String  -> StringValue(handler)
                    is Number  -> NumberValue(handler.toDouble())
                    is Boolean -> BoolValue(handler)
                    is Vec2    -> Vec2Value(handler.x.toDouble(), handler.y.toDouble())
                    is Vec3    -> Vec3Value(handler.x.toDouble(), handler.y.toDouble(), handler.z.toDouble())
                    is Color   -> ColorValue(handler.r, handler.g, handler.b, handler.a)
                    else       -> throw IllegalAccessException("Unknown data type: ${handler::class.simpleName}")
                }
            }
        }
        fun function(str: String, handler: (FunctionArguments, ExpressionContext) -> Any) {
            if (ideFunctions.none { it.strFull == str }) {
                ideFunctions += ExpressionSignature(
                    strFull = str,
                    strHighlight = str.split('(').first(),
                    strReturn = "Any"
                )
            }
            addFunction(str) { args, ctx ->
                val handler = handler(args, ctx)

                when (handler) {
                    is String  -> StringValue(handler)
                    is Number  -> NumberValue(handler.toDouble())
                    is Boolean -> BoolValue(handler)
                    is Vec2    -> Vec2Value(handler.x.toDouble(), handler.y.toDouble())
                    is Vec3    -> Vec3Value(handler.x.toDouble(), handler.y.toDouble(), handler.z.toDouble())
                    is Color   -> ColorValue(handler.r, handler.g, handler.b, handler.a)
                    else       -> throw IllegalAccessException("Unknown data type: ${handler::class.simpleName}")
                }
            }
        }

        function("math.sin(value)") { args, _ -> Mth.sin(args.double("value")) }
        function("math.cos(value)") { args, _ -> Mth.cos(args.double("value")) }

        function("math.sin_deg(value)") { args, _ -> Mth.sin(args.double("value") * (Mth.PI / 180.0f)) }
        function("math.cos_deg(value)") { args, _ -> Mth.cos(args.double("value") * (Mth.PI / 180.0f)) }

        // Static (Cached) //
        function("math.randi(key,start,end)") { args, ctx ->
            val key   = args.string("key")
            val start = args.double("start").toInt()
            val end   = args.double("end").toInt()

            val cache = ctx.cache ?: return@function Mth.nextInt(randomSource, start, end)
            val fullKey = "randi:$key:$start:$end"

            if (!cache.containsKey(fullKey))
                cache[fullKey] = Mth.nextInt(randomSource, start, end)

            cache[fullKey] as Number
        }

        function("math.randf(key,start,end)") { args, ctx ->
            val key   = args.string("key")
            val start = args.double("start").toFloat()
            val end   = args.double("end").toFloat()

            val cache = ctx.cache ?: return@function Mth.nextFloat(randomSource, start, end)
            val fullKey = "randf:$key:$start:$end"

            if (!cache.containsKey(fullKey))
                cache[fullKey] = Mth.nextFloat(randomSource, start, end)

            cache[fullKey] as Number
        }
        function("math.rand(key)") { args, ctx ->
            val key   = args.string("key")

            val cache = ctx.cache ?: return@function Mth.nextFloat(randomSource, -1.0f, 1.0f)
            val fullKey = "rand:$key"

            if (!cache.containsKey(fullKey))
                cache[fullKey] = Mth.nextFloat(randomSource, -1.0f, 1.0f)

            cache[fullKey] as Number
        }

        // Dynamic //
        function("math.randi(start,end)") { args, _ ->
            val start = args.double("start").toInt()
            val end   = args.double("end").toInt()

            Mth.nextInt(randomSource, start, end)
        }
        function("math.randf(start,end)") { args, _ ->
            val start = args.double("start").toFloat()
            val end   = args.double("end").toFloat()

            Mth.nextFloat(randomSource, start, end)
        }
        function("math.rand()") { _, _ ->
            Mth.nextFloat(randomSource, -1.0f, 1.0f)
        }

        function("vec2(x,y)") { args, _ ->
            val x = args.double("x").toFloat()
            val y= args.double("y").toFloat()

            Vec2(x, y)
        }
        function("vec3(x,y,z)") { args, _ ->
            val x = args.double("x").toFloat()
            val y = args.double("y").toFloat()
            val z = args.double("z").toFloat()

            Vec3(x, y, z)
        }

        function("color(r,g,b)") { args, _ ->
            val r = args.double("r").toInt()
            val g = args.double("g").toInt()
            val b = args.double("b").toInt()

            Color(r, g, b, 255)
        }
        function("color(r,g,b,a)") { args, _ ->
            val r = args.double("r").toInt()
            val g = args.double("g").toInt()
            val b = args.double("b").toInt()
            val a = args.double("a").toInt()

            Color(r, g, b, a)
        }
    }

    override fun onInitializeClient() {
        LOGGER.info("Initialization...")

        registerBuiltinIdeVariables()
        GoFindWinClientConfig.Client.init()

        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloader("resources".mident, GoFindWinClientReload)

        GoFindWinClientKeybind.init()
        ClientTickEvents.END_CLIENT_TICK.register {
            particleManager?.tick()
        }

        HudElementRegistry.addFirst("particle".mident) { guiGraphics, deltaTracker ->
            particleManager?.draw(guiGraphics, deltaTracker, 0f, 0f)
        }
    }

    val GoFindWinClientReload =
        ResourceManagerReloadListener { resourceManager: ResourceManager ->
            GoFindWinClientTranslate.reload(resourceManager)
            ideHighlightRule = emptyList()
        }

}
