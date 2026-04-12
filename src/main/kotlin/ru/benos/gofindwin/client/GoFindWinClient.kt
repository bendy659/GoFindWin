package ru.benos.gofindwin.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
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
import ru.benos.gofindwin.client.data.EffectProfileData
import ru.benos.gofindwin.client.data.Vec3
import ru.benos_codex.client.BoolValue
import ru.benos_codex.client.ColorValue
import ru.benos_codex.client.ExpressionContext
import ru.benos_codex.client.ExpressionEngine
import ru.benos_codex.client.FunctionArguments
import ru.benos_codex.client.NumberValue
import ru.benos_codex.client.StringValue
import ru.benos_codex.client.Vec2Value
import ru.benos_codex.client.Vec3Value
import ru.benos_codex.client.gui.auto_layout.ui.UiTextHighlightRule

@Environment(EnvType.CLIENT)
object GoFindWinClient : ClientModInitializer {
    val LOGGER: Logger = LoggerFactory.getLogger("${GoFindWinConst.MOD_NAME} | Client")

    val randomSource: RandomSource = RandomSource.create()

    data class ExpressionSignature(val d: String, val r: String)
    val ideVariables: MutableList<ExpressionSignature> = mutableListOf()
    val ideFunctions: MutableList<ExpressionSignature> = mutableListOf()
    val ideSpecials : MutableList<ExpressionSignature> = mutableListOf()
    var ideHighlightRule: List<UiTextHighlightRule> = emptyList()

    var effectProfiles: List<EffectProfileData> = emptyList()

    var EXPRESSION_ENGINE: ExpressionEngine = ExpressionEngine().apply {
        fun function(s: String, handler: (FunctionArguments, ExpressionContext) -> Any) {
            ideFunctions += ExpressionSignature(s, handler::class.simpleName ?: "null")

            addFunction(s) { args, ctx ->
                when (val handler = handler(args, ctx)) {
                    is String  -> StringValue(handler)
                    is Number  -> NumberValue(handler.toDouble())
                    is Boolean -> BoolValue(handler)
                    is Vec2    -> Vec2Value(handler.x.toDouble(), handler.y.toDouble())
                    is Vec3 -> Vec3Value(handler.x.toDouble(), handler.y.toDouble(), handler.z.toDouble())
                    is Color -> ColorValue(handler.r, handler.g, handler.b, handler.a)
                    else       -> throw IllegalAccessException("Unknown data type: ${handler::class.simpleName}")
                }
            }
        }

        function("math.sin(value)") { args, _ -> Mth.sin(args.double("value")) }
        function("math.cos(value)") { args, _ -> Mth.cos(args.double("value")) }
        function("math.sin_deg(value)") { args, _ -> Mth.sin(args.double("value") * (Mth.PI / 180.0f)) }
        function("math.cos_deg(value)") { args, _ -> Mth.cos(args.double("value") * (Mth.PI / 180.0f)) }
        function("math.randi(start,end)") { args, _ -> Mth.nextInt(randomSource, args.double("start").toInt(), args.double("end").toInt()) }
        function("math.randf(start,end)") { args, _ -> Mth.nextFloat(randomSource,args.double("start").toFloat(), args.double("end").toFloat()) }
        function("math.rand()") { _, _ -> Mth.nextFloat(randomSource, -1.0f, 1.0f) }

        function("vec2(x,y)") { args, _ -> Vec2(args.double("x").toFloat(), args.double("y").toFloat()) }
        function("vec3(x,y,z)") { args, _ ->
            Vec3(
                args.double("x").toFloat(),
                args.double("y").toFloat(),
                args.double("z").toFloat()
            )
        }
        function("color(r, g, b, a)") { args, _ ->
            Color(
                args.double("r").toInt(),
                args.double("g").toInt(),
                args.double("b").toInt(),
                args.double("a").toInt()
            )
        }
    }

    override fun onInitializeClient() {
        LOGGER.info("Initialization...")

        GoFindWinClientConfig.init()

        ResourceLoader.get(PackType.CLIENT_RESOURCES)
            .registerReloader("resources".mident, GoFindWinClientReload)

        GoFindWinClientKeybind.init()
        ParticleManager.init

        HudElementRegistry.addFirst("particle".mident) { guiGraphics, deltaTracker -> }
    }

    val GoFindWinClientReload = ResourceManagerReloadListener { resourceManager: ResourceManager ->
        GoFindWinClientTranslate.reload(resourceManager)
        ideHighlightRule = emptyList()
    }

}
