package ru.benos_codex.gofindwin.client.hud.effects

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import ru.benos_codex.gofindwin.client.hud.TimerHudConfigManager
import kotlin.math.max
import kotlin.random.Random

object HudCelebrationEffectsClient {
    private val LOGGER = LoggerFactory.getLogger("GoFindWin/HudCelebrationEffects")
    private const val MAX_ACTIVE_EFFECTS = 320
    private enum class RenderLayer { LIVE, PREVIEW }
    private val liveEffects = mutableListOf<EffectInstance>()
    private val previewEffects = mutableListOf<EffectInstance>()
    private val mobCache = mutableMapOf<String, LivingEntity>()
    private val textureSizeCache = mutableMapOf<String, Pair<Int, Int>>()
    private var lastRenderAtNs: Long = System.nanoTime()
    @Volatile
    private var pendingCategory: FinishEffectCategory? = null
    @Volatile
    private var pendingPreviewConfig: HudCelebrationConfig? = null
    private val liveSpawners = mutableMapOf<String, SpawnerState>()
    private val previewSpawners = mutableMapOf<String, SpawnerState>()

    fun requestBurst(category: FinishEffectCategory) {
        pendingCategory = category
    }

    fun requestPreview(config: HudCelebrationConfig) {
        pendingPreviewConfig = config
    }

    fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, bounds: HudRenderBounds, targetItemId: String?, preview: Boolean = false) {
        val deltaSeconds = max(0.01f, deltaTracker.getRealtimeDeltaTicks() / 20f)
        render(guiGraphics, bounds, targetItemId, deltaSeconds, preview)
    }

    fun render(guiGraphics: GuiGraphics, bounds: HudRenderBounds, targetItemId: String?, preview: Boolean = false) {
        val now = System.nanoTime()
        val elapsedSeconds = ((now - lastRenderAtNs).coerceAtLeast(1L) / 1_000_000_000.0).toFloat()
        lastRenderAtNs = now
        render(guiGraphics, bounds, targetItemId, elapsedSeconds.coerceIn(0.0005f, 0.05f), preview)
    }

    private fun render(guiGraphics: GuiGraphics, bounds: HudRenderBounds, targetItemId: String?, deltaSeconds: Float, preview: Boolean) {
        val layer = if (preview) RenderLayer.PREVIEW else RenderLayer.LIVE
        val effects = effectsFor(layer)
        val previewConfig = pendingPreviewConfig
        if (preview && previewConfig != null) {
            if (previewConfig.enabled) {
                clearLayer(RenderLayer.PREVIEW)
                activateEmitters(previewConfig, bounds, targetItemId, "preview", RenderLayer.PREVIEW)
            }
            pendingPreviewConfig = null
        }

        val pendingCategory = pendingCategory
        if (pendingCategory != null) {
            val presetId = TimerHudConfigManager.config.finishEffects.profileId(pendingCategory)
            val preset = HudCelebrationConfigManager.getPreset(presetId)
            if (preset.enabled) {
                activateEmitters(preset, bounds, targetItemId, presetId, RenderLayer.LIVE)
            }
            this.pendingCategory = null
        }

        tickSpawners(bounds, targetItemId, deltaSeconds, layer)

        if (effects.isEmpty()) return

        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.age += deltaSeconds
            if (effect.age >= effect.life) {
                iterator.remove()
                continue
            }

            val movementVars = mapOf(
                "life_time" to effect.lifeTicks.toDouble(),
                "life_factor" to (effect.age / effect.life).coerceIn(0f, 1f).toDouble(),
                "age_tick" to (effect.age * 20f).toDouble(),
                "age_frame" to (effect.age * 60f).toDouble(),
                "age_tick_delta" to (deltaSeconds * 20f).toDouble(),
                "age_frame_delta" to (deltaSeconds * 60f).toDouble(),
                "rand" to effect.rand.toDouble(),
                "rand_x" to effect.randX.toDouble(),
                "rand_y" to effect.randY.toDouble(),
                "pos_x" to (effect.x - effect.spawnX).toDouble(),
                "pos_y" to (effect.y - effect.spawnY).toDouble(),
                "start_pos_x" to effect.startPosX.toDouble(),
                "start_pos_y" to effect.startPosY.toDouble()
            )
            val dx = evaluatedFloat(effect.velocityXExpression, effect.velocityXFallback, movementVars)
            val dy = evaluatedFloat(effect.velocityYExpression, effect.velocityYFallback, movementVars)
            effect.gravityVelocity += effect.gravity * deltaSeconds
            if (effect.positionXExpression.isNotBlank()) {
                effect.x = effect.spawnX + evaluatedFloat(effect.positionXExpression, effect.positionXFallback, movementVars)
            } else {
                effect.x += dx * deltaSeconds
            }
            if (effect.positionYExpression.isNotBlank()) {
                effect.y = effect.spawnY + evaluatedFloat(effect.positionYExpression, effect.positionYFallback, movementVars)
            } else {
                effect.y += (dy + effect.gravityVelocity) * deltaSeconds
            }

            if (shouldRenderEffect(effect, bounds)) {
                renderEffect(guiGraphics, effect, deltaSeconds)
            }
        }
    }

    fun clear() {
        liveEffects.clear()
        previewEffects.clear()
        liveSpawners.clear()
        previewSpawners.clear()
        pendingCategory = null
        pendingPreviewConfig = null
        lastRenderAtNs = System.nanoTime()
    }

    private fun clearLayer(layer: RenderLayer) {
        effectsFor(layer).clear()
        spawnersFor(layer).clear()
    }

    private fun effectsFor(layer: RenderLayer): MutableList<EffectInstance> = when (layer) {
        RenderLayer.LIVE -> liveEffects
        RenderLayer.PREVIEW -> previewEffects
    }

    private fun spawnersFor(layer: RenderLayer): MutableMap<String, SpawnerState> = when (layer) {
        RenderLayer.LIVE -> liveSpawners
        RenderLayer.PREVIEW -> previewSpawners
    }

    private fun activateEmitters(config: HudCelebrationConfig, bounds: HudRenderBounds, targetItemId: String?, keyPrefix: String, layer: RenderLayer) {
        if (!config.enabled) return
        config.emitters.filter { it.enabled }.forEachIndexed { index, emitter ->
            when (emitter.spawnMode) {
                HudCelebrationSpawnMode.SINGLE -> spawnEmitter(emitter, bounds, targetItemId, layer)
                HudCelebrationSpawnMode.CONTINUOUS -> {
                    val key = "$keyPrefix:$index"
                    spawnEmitter(emitter, bounds, targetItemId, layer)
                    spawnersFor(layer)[key] = SpawnerState(
                        emitter = emitter,
                        remainingSec = (evaluatedInt(emitter.lifetimeExpression, emitter.lifetimeTicks, expressionSeedVariables(emitter, bounds)).coerceAtLeast(1) / 20f),
                        accumulatorSec = 0f
                    )
                }
            }
        }
    }

    private fun tickSpawners(bounds: HudRenderBounds, targetItemId: String?, deltaSeconds: Float, layer: RenderLayer) {
        val spawners = spawnersFor(layer)
        if (spawners.isEmpty()) return
        val iterator = spawners.iterator()
        while (iterator.hasNext()) {
            val (_, state) = iterator.next()
            state.remainingSec -= deltaSeconds
            if (state.remainingSec <= 0f) {
                iterator.remove()
                continue
            }
            state.accumulatorSec += deltaSeconds
            val periodSec = (evaluatedFloat(state.emitter.spawnPeriodExpression, state.emitter.spawnPeriodMs, expressionSeedVariables(state.emitter, bounds)) / 1000f).coerceAtLeast(0.01f)
            while (state.accumulatorSec >= periodSec) {
                state.accumulatorSec -= periodSec
                spawnEmitter(state.emitter, bounds, targetItemId, layer)
            }
        }
    }

    private fun spawnEmitter(emitter: HudCelebrationEmitterConfig, bounds: HudRenderBounds, targetItemId: String?, layer: RenderLayer) {
        val effects = effectsFor(layer)
        val count = evaluatedInt(emitter.countExpression, emitter.count, expressionSeedVariables(emitter, bounds)).coerceAtLeast(1)
        repeat(count) {
            createEffectInstance(emitter, bounds, targetItemId)?.let(effects::add)
        }
        val overflow = effects.size - MAX_ACTIVE_EFFECTS
        if (overflow > 0) {
            repeat(overflow) {
                if (effects.isNotEmpty()) effects.removeAt(0)
            }
        }
    }

    private fun expressionSeedVariables(emitter: HudCelebrationEmitterConfig, bounds: HudRenderBounds): Map<String, Double> = mapOf(
        "life_time" to emitter.lifetimeTicks.toDouble(),
        "life_factor" to 0.0,
        "age_tick" to 0.0,
        "age_frame" to 0.0,
        "age_tick_delta" to 0.0,
        "age_frame_delta" to 0.0,
        "rand" to 0.0,
        "rand_x" to 0.0,
        "rand_y" to 0.0,
        "pos_x" to 0.0,
        "pos_y" to 0.0,
        "start_pos_x" to 0.0,
        "start_pos_y" to 0.0,
        "hud_left" to bounds.left.toDouble(),
        "hud_right" to bounds.right.toDouble(),
        "hud_top" to bounds.top.toDouble(),
        "hud_bottom" to bounds.bottom.toDouble(),
        "hud_center_x" to bounds.centerX.toDouble(),
        "hud_center_y" to bounds.centerY.toDouble()
    )

    private fun createEffectInstance(
        emitter: HudCelebrationEmitterConfig,
        bounds: HudRenderBounds,
        targetItemId: String?
    ): EffectInstance? {
        val rand = Random.nextFloat() * 2f - 1f
        val randX = Random.nextFloat() * 2f - 1f
        val randY = Random.nextFloat() * 2f - 1f
        val originVariables = expressionSeedVariables(emitter, bounds) + mapOf(
            "rand" to rand.toDouble(),
            "rand_x" to randX.toDouble(),
            "rand_y" to randY.toDouble()
        )
        val origin = resolveOrigin(bounds, emitter, originVariables)
        val lifeTicks = evaluatedInt(emitter.lifetimeExpression, emitter.lifetimeTicks, mapOf(
            "life_time" to emitter.lifetimeTicks.toDouble(),
            "life_factor" to 0.0,
            "age_tick" to 0.0,
            "age_frame" to 0.0,
            "age_tick_delta" to 0.0,
            "age_frame_delta" to 0.0,
            "rand" to rand.toDouble(),
            "rand_x" to randX.toDouble(),
            "rand_y" to randY.toDouble(),
            "pos_x" to 0.0,
            "pos_y" to 0.0,
            "start_pos_x" to 0.0,
            "start_pos_y" to 0.0,
            "hud_left" to bounds.left.toDouble(),
            "hud_right" to bounds.right.toDouble(),
            "hud_top" to bounds.top.toDouble(),
            "hud_bottom" to bounds.bottom.toDouble(),
            "hud_center_x" to bounds.centerX.toDouble(),
            "hud_center_y" to bounds.centerY.toDouble()
        )).coerceAtLeast(1)
        val life = lifeTicks / 20f
        val variables = mapOf(
            "life_time" to lifeTicks.toDouble(),
            "life_factor" to 0.0,
            "age_tick" to 0.0,
            "age_frame" to 0.0,
            "age_tick_delta" to 0.0,
            "age_frame_delta" to 0.0,
            "rand" to rand.toDouble(),
            "rand_x" to randX.toDouble(),
            "rand_y" to randY.toDouble()
        )
        val itemStack = when {
            emitter.renderType != HudCelebrationRenderType.ITEMSTACK -> null
            emitter.useTargetItem -> resolveItemStack(targetItemId) ?: resolveItemStack(emitter.itemId) ?: ItemStack(Items.BARRIER)
            else -> resolveItemStack(emitter.itemId)
        }

        val mob = when (emitter.renderType) {
            HudCelebrationRenderType.MOB -> resolveMob(emitter.mobId)
            else -> null
        }

        val sequenceTokens = parseFrameTokens(emitter.textureFrameOrder, variables)
        val textureId = when (emitter.renderType) {
            HudCelebrationRenderType.TEXTURE -> when (emitter.textureMode) {
                HudCelebrationTextureMode.SEQUENCE -> {
                    val firstToken = sequenceTokens.firstOrNull() ?: "0"
                    resolveSequenceTextureIdentifier(emitter.textureId, firstToken)
                        ?: resolveSequenceTextureIdentifier(emitter.textureId, "0")
                        ?: resolveSequenceTextureIdentifier(emitter.textureId, "1")
                }
                else -> resolveTextureIdentifier(emitter.textureId)
            }
            else -> null
        }
        val textureSize = textureId?.let { resolveTextureSize(it) }

        if (emitter.renderType == HudCelebrationRenderType.ITEMSTACK && itemStack == null) return null
        if (emitter.renderType == HudCelebrationRenderType.MOB && mob == null) return null
        if (emitter.renderType == HudCelebrationRenderType.TEXTURE && textureId == null) return null

        val colorRaw = emitter.colorPalette.joinToString(",")
        val colorLerpRaw = emitter.colorLerpPalette.joinToString(",")
        val colorStart = parsePaletteColor(emitter.colorPalette)
        val colorEnd = if (emitter.colorLerpPalette.isNotEmpty()) parsePaletteColor(emitter.colorLerpPalette) else colorStart
        val textureTintRaw = emitter.textureTintColor
        val textureTintLerpRaw = emitter.textureTintLerpPalette.joinToString(",")
        val textureTintStart = parsePaletteColor(parseColorPalette(emitter.textureTintColor).ifEmpty { listOf("#FFFFFF") })
        val textureTintEnd = if (emitter.textureTintLerpPalette.isNotEmpty()) parsePaletteColor(emitter.textureTintLerpPalette) else textureTintStart

        val spawnX = origin.first + Random.nextFloat() * evaluatedFloat(emitter.randomOffsetXExpression, emitter.randomOffsetX, variables) * 2f -
            evaluatedFloat(emitter.randomOffsetXExpression, emitter.randomOffsetX, variables)
        val spawnY = origin.second + Random.nextFloat() * evaluatedFloat(emitter.randomOffsetYExpression, emitter.randomOffsetY, variables) * 2f -
            evaluatedFloat(emitter.randomOffsetYExpression, emitter.randomOffsetY, variables)

        return EffectInstance(
            renderType = emitter.renderType,
            x = spawnX,
            y = spawnY,
            spawnX = spawnX,
            spawnY = spawnY,
            startPosX = spawnX - origin.first,
            startPosY = spawnY - origin.second,
            velocityXExpression = emitter.initialVelocityXExpression,
            velocityYExpression = emitter.initialVelocityYExpression,
            velocityXFallback = emitter.initialVelocityX,
            velocityYFallback = emitter.initialVelocityY,
            positionXExpression = emitter.positionXExpression.trim(),
            positionYExpression = emitter.positionYExpression.trim(),
            positionXFallback = emitter.positionX,
            positionYFallback = emitter.positionY,
            gravity = evaluatedFloat(emitter.gravityExpression, emitter.gravity, variables),
            gravityVelocity = 0f,
            lifeTicks = lifeTicks,
            life = life,
            age = 0f,
            rotationExpression = emitter.initialRotationExpression,
            rotationFallback = emitter.initialRotation,
            sizeExpression = emitter.sizeExpression.trim(),
            sizeFallback = emitter.size,
            alphaExpression = emitter.alphaExpression.trim(),
            alphaFallback = emitter.alpha,
            rotationRandomOffset = 0f,
            rand = rand,
            randX = randX,
            randY = randY,
            startSize = randomRange(emitter.startSizeMin, emitter.startSizeMax),
            endSize = randomRange(emitter.endSizeMin, emitter.endSizeMax),
            startAlpha = randomRange(emitter.startAlphaMin, emitter.startAlphaMax),
            endAlpha = randomRange(emitter.endAlphaMin, emitter.endAlphaMax),
            startColor = colorStart,
            endColor = colorEnd,
            colorExpressionRaw = colorRaw,
            colorLerpExpressionRaw = colorLerpRaw,
            colorLerpFactorExpression = emitter.colorLerpFactorExpression,
            textureMode = emitter.textureMode,
            texturePlaybackMode = emitter.texturePlaybackMode,
            textureIdPattern = emitter.textureId,
            textureId = textureId,
            textureUvX = evaluatedInt(emitter.textureUvXExpression, emitter.textureUvX, variables).coerceAtLeast(0),
            textureUvY = evaluatedInt(emitter.textureUvYExpression, emitter.textureUvY, variables).coerceAtLeast(0),
            textureUvWidth = evaluatedInt(emitter.textureUvWidthExpression, emitter.textureUvWidth, variables).coerceAtLeast(1),
            textureUvHeight = evaluatedInt(emitter.textureUvHeightExpression, emitter.textureUvHeight, variables).coerceAtLeast(1),
            textureTintStartColor = textureTintStart,
            textureTintEndColor = textureTintEnd,
            textureTintExpressionRaw = textureTintRaw,
            textureTintLerpExpressionRaw = textureTintLerpRaw,
            textureTintLerpFactorExpression = emitter.textureTintLerpFactorExpression,
            textureFramesX = evaluatedInt(emitter.textureFramesXExpression, emitter.textureFramesX, variables).coerceAtLeast(1),
            textureFramesY = evaluatedInt(emitter.textureFramesYExpression, emitter.textureFramesY, variables).coerceAtLeast(1),
            textureFrameTimeMs = evaluatedFloat(emitter.textureFrameTimeMsExpression, emitter.textureFrameTimeMs, variables).coerceAtLeast(10f),
            textureInterpolate = emitter.textureInterpolate,
            textureFrameOrder = emitter.textureFrameOrder,
            texturePixelWidth = textureSize?.first ?: 256,
            texturePixelHeight = textureSize?.second ?: 256,
            textContent = emitter.textContent.replace("\\n", "\n"),
            textColor = parseColor(emitter.textColor),
            textShadowEnabled = emitter.textShadowEnabled,
            textShadowColor = parseColor(emitter.textShadowColor),
            textScale = evaluatedFloat(emitter.textScaleExpression, emitter.textScale, variables).coerceAtLeast(0.25f),
            itemStack = itemStack,
            mob = mob
        )
    }

    private fun renderEffect(guiGraphics: GuiGraphics, effect: EffectInstance, deltaSeconds: Float) {
        val progress = (effect.age / effect.life).coerceIn(0f, 1f)
        val size = if (effect.sizeExpression.isNotBlank()) {
            evaluatedFloat(
                effect.sizeExpression,
                effect.sizeFallback,
                mapOf(
                    "life_time" to effect.lifeTicks.toDouble(),
                    "life_factor" to progress.toDouble(),
                    "age_tick" to (effect.age * 20f).toDouble(),
                    "age_frame" to (effect.age * 60f).toDouble(),
                    "age_tick_delta" to (deltaSeconds * 20f).toDouble(),
                    "age_frame_delta" to (deltaSeconds * 60f).toDouble(),
                    "rand" to effect.rand.toDouble(),
                    "rand_x" to effect.randX.toDouble(),
                    "rand_y" to effect.randY.toDouble(),
                    "pos_x" to (effect.x - effect.spawnX).toDouble(),
                    "pos_y" to (effect.y - effect.spawnY).toDouble(),
                    "start_pos_x" to effect.startPosX.toDouble(),
                    "start_pos_y" to effect.startPosY.toDouble()
                )
            ).coerceAtLeast(1f)
        } else {
            lerp(effect.startSize, effect.endSize, progress).coerceAtLeast(1f)
        }
        val rotation = evaluatedFloat(
            effect.rotationExpression,
            effect.rotationFallback,
            mapOf(
                "life_time" to effect.lifeTicks.toDouble(),
                "life_factor" to progress.toDouble(),
                "age_tick" to (effect.age * 20f).toDouble(),
                "age_frame" to (effect.age * 60f).toDouble(),
                "age_tick_delta" to (deltaSeconds * 20f).toDouble(),
                "age_frame_delta" to (deltaSeconds * 60f).toDouble(),
                "rand" to effect.rand.toDouble(),
                "rand_x" to effect.randX.toDouble(),
                "rand_y" to effect.randY.toDouble(),
                "pos_x" to (effect.x - effect.spawnX).toDouble(),
                "pos_y" to (effect.y - effect.spawnY).toDouble(),
                "start_pos_x" to effect.startPosX.toDouble(),
                "start_pos_y" to effect.startPosY.toDouble()
            )
        ) + effect.rotationRandomOffset
        val alpha = if (effect.alphaExpression.isNotBlank()) {
            evaluatedFloat(
                effect.alphaExpression,
                effect.alphaFallback,
                mapOf(
                    "life_time" to effect.lifeTicks.toDouble(),
                    "life_factor" to progress.toDouble(),
                    "age_tick" to (effect.age * 20f).toDouble(),
                    "age_frame" to (effect.age * 60f).toDouble(),
                    "age_tick_delta" to (deltaSeconds * 20f).toDouble(),
                    "age_frame_delta" to (deltaSeconds * 60f).toDouble(),
                    "rand" to effect.rand.toDouble(),
                    "rand_x" to effect.randX.toDouble(),
                    "rand_y" to effect.randY.toDouble(),
                    "pos_x" to (effect.x - effect.spawnX).toDouble(),
                    "pos_y" to (effect.y - effect.spawnY).toDouble(),
                    "start_pos_x" to effect.startPosX.toDouble(),
                    "start_pos_y" to effect.startPosY.toDouble()
                )
            ).coerceIn(0f, 1f)
        } else {
            lerp(effect.startAlpha, effect.endAlpha, progress).coerceIn(0f, 1f)
        }

        when (effect.renderType) {
            HudCelebrationRenderType.PARTICLE -> renderParticle(guiGraphics, effect, size, rotation, alpha, progress, deltaSeconds)
            HudCelebrationRenderType.TEXTURE -> renderTexture(guiGraphics, effect, size, rotation, alpha)
            HudCelebrationRenderType.ITEMSTACK -> renderItemStack(guiGraphics, effect, size, rotation)
            HudCelebrationRenderType.TEXT -> renderText(guiGraphics, effect, rotation, alpha)
            HudCelebrationRenderType.MOB -> renderMob(guiGraphics, effect, size)
        }
    }

    private fun renderParticle(guiGraphics: GuiGraphics, effect: EffectInstance, size: Float, rotation: Float, alpha: Float, progress: Float, deltaSeconds: Float) {
        val variables = renderVariables(effect, progress, deltaSeconds)
        val colorFactor = if (effect.colorLerpFactorExpression.isNotBlank()) {
            evaluatedFloat(
                effect.colorLerpFactorExpression,
                progress,
                variables
            ).coerceIn(0f, 1f)
        } else progress
        val color = when {
            isDynamicColorExpression(effect.colorExpressionRaw) ->
                MathExpression.evaluateColor(effect.colorExpressionRaw, variables) ?: effect.startColor
            isDynamicColorExpression(effect.colorLerpExpressionRaw) ->
                blendColors(effect.startColor, MathExpression.evaluateColor(effect.colorLerpExpressionRaw, variables) ?: effect.endColor, colorFactor)
            else -> blendColors(effect.startColor, effect.endColor, colorFactor)
        }
        val argb = (((alpha * 255f).toInt().coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)
        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(effect.x, effect.y)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        val half = size / 2f
        guiGraphics.fill((-half).toInt(), (-half).toInt(), half.toInt(), half.toInt(), argb)
        pose.popMatrix()
    }

    private fun shouldRenderEffect(effect: EffectInstance, bounds: HudRenderBounds): Boolean {
        val margin = 96f
        return effect.x >= bounds.left - margin &&
            effect.x <= bounds.right + margin &&
            effect.y >= bounds.top - margin &&
            effect.y <= bounds.bottom + margin
    }

    private fun renderTexture(guiGraphics: GuiGraphics, effect: EffectInstance, size: Float, rotation: Float, alpha: Float) {
        if (alpha <= 0.001f) return
        val totalFrames = when (effect.textureMode) {
            HudCelebrationTextureMode.SINGLE -> 1
            HudCelebrationTextureMode.SHEET -> (effect.textureFramesX * effect.textureFramesY).coerceAtLeast(1)
            HudCelebrationTextureMode.SEQUENCE -> parsedTextureFrameTokens(effect).size.coerceAtLeast(1)
        }
        val progress = (effect.age / effect.life).coerceIn(0f, 1f)
        val variables = renderVariables(effect, progress, 0f)
        val textureTintFactor = if (effect.textureTintLerpFactorExpression.isNotBlank()) {
            evaluatedFloat(
                effect.textureTintLerpFactorExpression,
                progress,
                variables
            ).coerceIn(0f, 1f)
        } else {
            progress
        }
        val dynamicTint = if (isDynamicColorExpression(effect.textureTintExpressionRaw)) {
            MathExpression.evaluateColor(effect.textureTintExpressionRaw, variables)
        } else {
            null
        }
        val baseTint = when {
            isDynamicColorExpression(effect.textureTintExpressionRaw) ->
                dynamicTint ?: effect.textureTintStartColor
            isDynamicColorExpression(effect.textureTintLerpExpressionRaw) ->
                blendColors(effect.textureTintStartColor, MathExpression.evaluateColor(effect.textureTintLerpExpressionRaw, variables) ?: effect.textureTintEndColor, textureTintFactor)
            else -> blendColors(effect.textureTintStartColor, effect.textureTintEndColor, textureTintFactor)
        }
        val r = (baseTint shr 16) and 0xFF
        val g = (baseTint shr 8) and 0xFF
        val b = baseTint and 0xFF
        if (effect.textureTintExpressionRaw.isNotBlank()) {
            LOGGER.info(
                "textureTint raw='{}' dynamic={} evaluated={} fallback={} baseTint=0x{} rgb=({}, {}, {}) alpha={}",
                effect.textureTintExpressionRaw,
                isDynamicColorExpression(effect.textureTintExpressionRaw),
                dynamicTint?.let { "0x%08X".format(it) } ?: "null",
                "0x%08X".format(effect.textureTintStartColor),
                "%08X".format(baseTint),
                r,
                g,
                b,
                alpha
            )
        }
        val animatedFrame = resolveAnimatedFrameInfo(effect, totalFrames)
        val currentSource = resolveTextureFrameSource(effect, animatedFrame.currentFrame) ?: return
        val nextSource = if (effect.textureInterpolate && animatedFrame.interpolation > 0.001f && animatedFrame.nextFrame != animatedFrame.currentFrame) {
            resolveTextureFrameSource(effect, animatedFrame.nextFrame)
        } else {
            null
        }
        if (nextSource != null) {
            drawTextureFrame(guiGraphics, size, rotation, effect, currentSource, alpha * (1f - animatedFrame.interpolation), r, g, b)
            drawTextureFrame(guiGraphics, size, rotation, effect, nextSource, alpha * animatedFrame.interpolation, r, g, b)
        } else {
            drawTextureFrame(guiGraphics, size, rotation, effect, currentSource, alpha, r, g, b)
        }
    }

    private fun drawTextureFrame(
        guiGraphics: GuiGraphics,
        size: Float,
        rotation: Float,
        effect: EffectInstance,
        source: TextureFrameSource,
        alpha: Float,
        r: Int,
        g: Int,
        b: Int
    ) {
        if (alpha <= 0.001f) return
        val tint = (((alpha * 255f).toInt().coerceIn(0, 255)) shl 24) or (r shl 16) or (g shl 8) or b
        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(effect.x, effect.y)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            source.textureId,
            (-size / 2f).toInt(),
            (-size / 2f).toInt(),
            source.uvX.toFloat(),
            source.uvY.toFloat(),
            size.toInt().coerceAtLeast(1),
            size.toInt().coerceAtLeast(1),
            effect.textureUvWidth,
            effect.textureUvHeight,
            source.textureWidth,
            source.textureHeight,
            tint
        )
        pose.popMatrix()
    }

    private fun resolveTextureFrameSource(effect: EffectInstance, frame: Int): TextureFrameSource? {
        val textureId = when (effect.textureMode) {
            HudCelebrationTextureMode.SINGLE, HudCelebrationTextureMode.SHEET -> effect.textureId
            HudCelebrationTextureMode.SEQUENCE -> {
                val token = parsedTextureFrameTokens(effect).getOrNull(frame) ?: frame.toString()
                resolveSequenceTextureIdentifier(effect.textureIdPattern, token) ?: effect.textureId
            }
        } ?: return null
        val textureSize = if (effect.textureMode == HudCelebrationTextureMode.SEQUENCE) resolveTextureSize(textureId) else null
        val uvX = when (effect.textureMode) {
            HudCelebrationTextureMode.SINGLE, HudCelebrationTextureMode.SEQUENCE -> effect.textureUvX
            HudCelebrationTextureMode.SHEET -> effect.textureUvX + (frame % effect.textureFramesX) * effect.textureUvWidth
        }
        val uvY = when (effect.textureMode) {
            HudCelebrationTextureMode.SINGLE, HudCelebrationTextureMode.SEQUENCE -> effect.textureUvY
            HudCelebrationTextureMode.SHEET -> effect.textureUvY + (frame / effect.textureFramesX) * effect.textureUvHeight
        }
        return TextureFrameSource(
            textureId = textureId,
            uvX = uvX,
            uvY = uvY,
            textureWidth = textureSize?.first ?: effect.texturePixelWidth,
            textureHeight = textureSize?.second ?: effect.texturePixelHeight
        )
    }

    private fun renderItemStack(guiGraphics: GuiGraphics, effect: EffectInstance, size: Float, rotation: Float) {
        val itemStack = effect.itemStack ?: return
        val scale = size / 16f
        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(effect.x, effect.y)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        pose.scale(scale, scale)
        guiGraphics.renderItem(itemStack, -8, -8)
        pose.popMatrix()
    }

    private fun renderText(guiGraphics: GuiGraphics, effect: EffectInstance, rotation: Float, alpha: Float) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val lines = effect.textContent.lines().ifEmpty { listOf("") }
        val lineHeight = font.lineHeight
        val maxWidth = lines.maxOfOrNull { font.width(it) } ?: 0
        val totalHeight = lines.size * lineHeight
        val argbText = (((alpha * 255f).toInt().coerceIn(0, 255)) shl 24) or (effect.textColor and 0x00FFFFFF)
        val argbShadow = (((alpha * 255f).toInt().coerceIn(0, 255)) shl 24) or (effect.textShadowColor and 0x00FFFFFF)

        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(effect.x, effect.y)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        pose.scale(effect.textScale, effect.textScale)
        val startX = -(maxWidth / 2f)
        val startY = -(totalHeight / 2f)
        lines.forEachIndexed { index, line ->
            val lineX = startX + (maxWidth - font.width(line)) / 2f
            val lineY = startY + index * lineHeight
            if (effect.textShadowEnabled) {
                guiGraphics.drawString(font, line, lineX.toInt() + 1, lineY.toInt() + 1, argbShadow, false)
            }
            guiGraphics.drawString(font, line, lineX.toInt(), lineY.toInt(), argbText, false)
        }
        pose.popMatrix()
    }

    private fun renderMob(guiGraphics: GuiGraphics, effect: EffectInstance, size: Float) {
        val mob = effect.mob ?: return
        val renderSize = size.toInt().coerceAtLeast(6)
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            guiGraphics,
            effect.x.toInt() - renderSize,
            effect.y.toInt() - renderSize,
            effect.x.toInt() + renderSize,
            effect.y.toInt() + renderSize,
            renderSize,
            0f,
            0f,
            0f,
            mob
        )
    }

    private fun resolveOrigin(
        bounds: HudRenderBounds,
        emitter: HudCelebrationEmitterConfig,
        variables: Map<String, Double>
    ): Pair<Float, Float> {
        val fallbackX = bounds.centerX.toFloat()
        val fallbackY = bounds.centerY.toFloat()
        val x = evaluatedFloat(emitter.originXExpression, if (emitter.originXExpression.isBlank()) fallbackX else emitter.originX, variables)
        val y = evaluatedFloat(emitter.originYExpression, if (emitter.originYExpression.isBlank()) fallbackY else emitter.originY, variables)
        return x to y
    }

    private fun resolveItemStack(itemId: String?): ItemStack? {
        val identifier = itemId?.let(Identifier::tryParse) ?: return null
        val item = BuiltInRegistries.ITEM.getValue(identifier)
        return ItemStack(item)
    }

    private fun resolveMob(mobId: String): LivingEntity? {
        mobCache[mobId]?.let { return it }

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val entityType = net.minecraft.world.entity.EntityType.byString(mobId).orElse(null) ?: return null
        val entity = entityType.create(level, EntitySpawnReason.COMMAND)
        val living = entity as? LivingEntity ?: return null
        mobCache[mobId] = living
        return living
    }

    private fun resolveTextureIdentifier(textureId: String): Identifier? {
        val parsed = Identifier.tryParse(textureId) ?: return null
        val path = parsed.path
        return when {
            path.startsWith("textures/") || path.endsWith(".png") -> parsed
            else -> Identifier.fromNamespaceAndPath(parsed.namespace, "textures/$path.png")
        }
    }

    private fun resolveSequenceTextureIdentifier(pattern: String, frameToken: String): Identifier? {
        val candidates = buildList {
            if (pattern.contains("%d")) {
                add(pattern.replace("%d", frameToken))
            } else {
                add(pattern)
            }
        }
        return candidates.firstNotNullOfOrNull { expanded ->
            resolveTextureIdentifier(expanded)?.takeIf { resolveTextureSize(it) != null }
        }
    }

    private fun resolveAnimatedFrameInfo(effect: EffectInstance, totalFrames: Int): AnimatedFrameInfo {
        if (totalFrames <= 1) return AnimatedFrameInfo(0, 0, 0f)
        val stepFloat = ((effect.age * 1000f) / effect.textureFrameTimeMs).coerceAtLeast(0f)
        val stepBase = stepFloat.toInt()
        return AnimatedFrameInfo(
            currentFrame = resolveAnimatedFrameAtStep(effect, totalFrames, stepBase),
            nextFrame = resolveAnimatedFrameAtStep(effect, totalFrames, stepBase + 1),
            interpolation = (stepFloat - stepBase).coerceIn(0f, 1f)
        )
    }

    private fun resolveAnimatedFrameAtStep(effect: EffectInstance, totalFrames: Int, step: Int): Int {
        val defaultOrder = (0 until totalFrames).toList()
        val customOrder = when (effect.textureMode) {
            HudCelebrationTextureMode.SEQUENCE -> parsedTextureFrameTokens(effect).indices.toList()
            else -> parsedTextureFrameOrder(effect).filter { it in 0 until totalFrames }
        }
        val order = if (customOrder.isEmpty()) defaultOrder else customOrder
        if (order.size == 1) return order.first()
        val index = when (effect.texturePlaybackMode) {
            HudCelebrationTexturePlaybackMode.ONCE -> step.coerceAtMost(order.lastIndex)
            HudCelebrationTexturePlaybackMode.LOOP -> step.floorMod(order.size)
            HudCelebrationTexturePlaybackMode.PING_PONG -> {
                val cycleLength = (order.size * 2 - 2).coerceAtLeast(1)
                val pingIndex = step.floorMod(cycleLength)
                if (pingIndex < order.size) pingIndex else cycleLength - pingIndex
            }
        }
        return order[index]
    }

    private fun parsedTextureFrameOrder(effect: EffectInstance): List<Int> =
        expandFrameOrder(effect.textureFrameOrder)

    private fun parsedTextureFrameTokens(effect: EffectInstance): List<String> =
        parseFrameTokens(effect.textureFrameOrder, renderVariables(effect, 0f, 0f))

    private fun expandFrameOrder(raw: String): List<Int> =
        raw.split(',', ';', ' ', '\n', '\r', '\t')
            .flatMap { token ->
                val trimmed = token.trim()
                when {
                    trimmed.isEmpty() -> emptyList()
                    ".." in trimmed -> {
                        val parts = trimmed.split("..", limit = 2)
                        val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                        val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        if (from == null || to == null) emptyList()
                        else if (from <= to) (from..to).toList()
                        else (from downTo to).toList()
                    }
                    else -> trimmed.toIntOrNull()?.let(::listOf) ?: emptyList()
                }
            }

    private fun parseFrameTokens(raw: String, variables: Map<String, Double>): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("rand_value(", ignoreCase = true) && trimmed.endsWith(")")) {
            val content = trimmed.substringAfter('(').dropLast(1)
            val options = content.split(',', ';')
                .flatMap { token -> expandFrameToken(token.trim()) }
                .filter { it.isNotEmpty() }
            if (options.isEmpty()) return emptyList()
            val choice = stableChoiceIndex(options, variables)
            return listOf(options[choice])
        }
        return raw.split(',', ';', ' ', '\n', '\r', '\t')
            .flatMap { token ->
                expandFrameToken(token.trim())
            }
    }

    private fun expandFrameToken(token: String): List<String> =
        when {
            token.isEmpty() -> emptyList()
            ".." in token -> {
                val parts = token.split("..", limit = 2)
                val from = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val to = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (from == null || to == null) listOf(token)
                else if (from <= to) (from..to).map(Int::toString)
                else (from downTo to).map(Int::toString)
            }
            else -> listOf(token)
        }

    private fun stableChoiceIndex(options: List<String>, variables: Map<String, Double>): Int {
        var hash = 17
        listOf("rand", "rand_x", "rand_y", "start_pos_x", "start_pos_y").forEach { key ->
            val value = variables[key] ?: 0.0
            val bits = java.lang.Double.doubleToLongBits(value)
            hash = 31 * hash + (bits xor (bits ushr 32)).toInt()
        }
        options.forEach { option -> hash = 31 * hash + option.hashCode() }
        return Math.floorMod(hash, options.size)
    }

    private fun resolveTextureSize(textureId: Identifier): Pair<Int, Int>? {
        textureSizeCache[textureId.toString()]?.let { return it }
        val minecraft = Minecraft.getInstance()
        val resource = minecraft.resourceManager.getResource(textureId).orElse(null) ?: return null
        return resource.open().use { stream ->
            com.mojang.blaze3d.platform.NativeImage.read(stream).use { image ->
                (image.width to image.height).also { textureSizeCache[textureId.toString()] = it }
            }
        }
    }

    private fun parsePaletteColor(palette: List<String>): Int {
        val chosen = palette.randomOrNull() ?: "#FFFFFF"
        return parseColor(chosen)
    }

    private fun isDynamicColorExpression(raw: String): Boolean =
        raw.contains("keyframe(", ignoreCase = true) || raw.contains(';') || raw.contains('{')

    private fun parseColorPalette(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseColor(raw: String): Int {
        val normalized = raw.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        return when (normalized.length) {
            6 -> normalized.toLongOrNull(16)?.toInt()?.let { (0xFF shl 24) or it }
            8 -> normalized.toLongOrNull(16)?.toInt()
            else -> 0xFFFFFFFF.toInt()
        } ?: 0xFFFFFFFF.toInt()
    }

    private fun randomRange(min: Float, max: Float): Float {
        if (max <= min) return min
        return Random.nextFloat() * (max - min) + min
    }

    private fun randomSigned(amount: Float): Float {
        if (amount <= 0f) return 0f
        return (Random.nextFloat() * 2f - 1f) * amount
    }

    private fun evaluatedFloat(expression: String, fallback: Float, variables: Map<String, Double>): Float {
        return MathExpression.evaluate(expression, variables)?.toFloat() ?: fallback
    }

    private fun evaluatedInt(expression: String, fallback: Int, variables: Map<String, Double>): Int {
        return (MathExpression.evaluate(expression, variables)?.toInt() ?: fallback)
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

    private fun renderVariables(effect: EffectInstance, progress: Float, deltaSeconds: Float): Map<String, Double> = mapOf(
        "life_time" to effect.lifeTicks.toDouble(),
        "life_factor" to progress.toDouble(),
        "age_tick" to (effect.age * 20f).toDouble(),
        "age_frame" to (effect.age * 60f).toDouble(),
        "age_tick_delta" to (deltaSeconds * 20f).toDouble(),
        "age_frame_delta" to (deltaSeconds * 60f).toDouble(),
        "rand" to effect.rand.toDouble(),
        "rand_x" to effect.randX.toDouble(),
        "rand_y" to effect.randY.toDouble(),
        "pos_x" to (effect.x - effect.spawnX).toDouble(),
        "pos_y" to (effect.y - effect.spawnY).toDouble(),
        "start_pos_x" to effect.startPosX.toDouble(),
        "start_pos_y" to effect.startPosY.toDouble()
    )

    private fun blendColors(start: Int, end: Int, progress: Float): Int {
        val sr = (start shr 16) and 0xFF
        val sg = (start shr 8) and 0xFF
        val sb = start and 0xFF
        val er = (end shr 16) and 0xFF
        val eg = (end shr 8) and 0xFF
        val eb = end and 0xFF
        val r = lerp(sr.toFloat(), er.toFloat(), progress).toInt().coerceIn(0, 255)
        val g = lerp(sg.toFloat(), eg.toFloat(), progress).toInt().coerceIn(0, 255)
        val b = lerp(sb.toFloat(), eb.toFloat(), progress).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private data class EffectInstance(
        val renderType: HudCelebrationRenderType,
        var x: Float,
        var y: Float,
        val spawnX: Float,
        val spawnY: Float,
        val startPosX: Float,
        val startPosY: Float,
        val lifeTicks: Int,
        val velocityXExpression: String,
        val velocityYExpression: String,
        val velocityXFallback: Float,
        val velocityYFallback: Float,
        val positionXExpression: String,
        val positionYExpression: String,
        val positionXFallback: Float,
        val positionYFallback: Float,
        val gravity: Float,
        var gravityVelocity: Float,
        val life: Float,
        var age: Float,
        val rotationExpression: String,
        val rotationFallback: Float,
        val sizeExpression: String,
        val sizeFallback: Float,
        val alphaExpression: String,
        val alphaFallback: Float,
        val rotationRandomOffset: Float,
        val rand: Float,
        val randX: Float,
        val randY: Float,
        val startSize: Float,
        val endSize: Float,
        val startAlpha: Float,
        val endAlpha: Float,
        val startColor: Int,
        val endColor: Int,
        val colorExpressionRaw: String,
        val colorLerpExpressionRaw: String,
        val colorLerpFactorExpression: String,
        val textureMode: HudCelebrationTextureMode,
        val texturePlaybackMode: HudCelebrationTexturePlaybackMode,
        val textureIdPattern: String,
        val textureId: Identifier?,
        val textureUvX: Int,
        val textureUvY: Int,
        val textureUvWidth: Int,
        val textureUvHeight: Int,
        val textureTintStartColor: Int,
        val textureTintEndColor: Int,
        val textureTintExpressionRaw: String,
        val textureTintLerpExpressionRaw: String,
        val textureTintLerpFactorExpression: String,
        val textureFramesX: Int,
        val textureFramesY: Int,
        val textureFrameTimeMs: Float,
        val textureInterpolate: Boolean,
        val textureFrameOrder: String,
        val texturePixelWidth: Int,
        val texturePixelHeight: Int,
        val textContent: String,
        val textColor: Int,
        val textShadowEnabled: Boolean,
        val textShadowColor: Int,
        val textScale: Float,
        val itemStack: ItemStack?,
        val mob: LivingEntity?
    )

    private data class AnimatedFrameInfo(
        val currentFrame: Int,
        val nextFrame: Int,
        val interpolation: Float
    )

    private data class TextureFrameSource(
        val textureId: Identifier,
        val uvX: Int,
        val uvY: Int,
        val textureWidth: Int,
        val textureHeight: Int
    )

    private data class SpawnerState(
        val emitter: HudCelebrationEmitterConfig,
        var remainingSec: Float,
        var accumulatorSec: Float
    )

    private fun Int.floorMod(divisor: Int): Int {
        if (divisor == 0) return this
        val result = this % divisor
        return if (result < 0) result + divisor else result
    }
}
