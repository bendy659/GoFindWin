package ru.benos_codex.gofindwin.hud

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import ru.benos_codex.gofindwin.run.RunPhase
import java.util.Optional

data class RunTimerHudPayload(
    val phaseName: String,
    val visible: Boolean,
    val elapsedMs: Long,
    val running: Boolean,
    val targetItemId: Optional<String>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RunTimerHudPayload> = TYPE

    fun toState(): RunTimerHudState = RunTimerHudState(
        phase = RunPhase.valueOf(phaseName),
        visible = visible,
        elapsedMs = elapsedMs,
        running = running
        ,
        targetItemId = targetItemId.orElse(null)
    )

    companion object {
        val TYPE: CustomPacketPayload.Type<RunTimerHudPayload> =
            CustomPacketPayload.createType("run_timer_hud_state")

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RunTimerHudPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RunTimerHudPayload::phaseName,
            ByteBufCodecs.BOOL,
            RunTimerHudPayload::visible,
            ByteBufCodecs.VAR_LONG,
            RunTimerHudPayload::elapsedMs,
            ByteBufCodecs.BOOL,
            RunTimerHudPayload::running,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            RunTimerHudPayload::targetItemId,
            ::RunTimerHudPayload
        )

        fun fromState(state: RunTimerHudState): RunTimerHudPayload = RunTimerHudPayload(
            phaseName = state.phase.name,
            visible = state.visible,
            elapsedMs = state.elapsedMs,
            running = state.running,
            targetItemId = Optional.ofNullable(state.targetItemId)
        )
    }
}
