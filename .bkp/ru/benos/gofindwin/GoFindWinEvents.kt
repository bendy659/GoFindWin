package ru.benos.gofindwin

import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.EventFactory

object GoFindWinEvents {
    fun interface PreStartEvent {
        fun onPreStarted()
    }
    fun interface TickingEvent {
        fun onTicking(tick: Long): Boolean
    }
    fun interface FinishEvent {
        fun onFinished(ticks: Long)
    }

    val PRE_START: Event<PreStartEvent> = EventFactory
        .createArrayBacked(PreStartEvent::class.java) { listeners ->
            PreStartEvent {
                listeners.forEach(PreStartEvent::onPreStarted)
            }
        }

    val TICKING: Event<TickingEvent> = EventFactory
        .createArrayBacked(TickingEvent::class.java) { listeners ->
            TickingEvent { tick ->
                listeners.any { it.onTicking(tick) }
            }
        }

    val FINISH: Event<FinishEvent> = EventFactory
        .createArrayBacked(FinishEvent::class.java) { listeners ->
            FinishEvent { ticks ->
                listeners.forEach { it.onFinished(ticks) }
            }
        }
}