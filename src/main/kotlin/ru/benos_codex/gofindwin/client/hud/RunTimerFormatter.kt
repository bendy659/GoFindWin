package ru.benos_codex.gofindwin.client.hud

object RunTimerFormatter {
    fun format(elapsedMs: Long): String {
        val clamped = elapsedMs.coerceAtLeast(0L)
        val milliseconds = (clamped % 1000L).toInt().coerceIn(0, 999)
        val totalSeconds = clamped / 1000L
        val seconds = (totalSeconds % 60L).toInt()
        val totalMinutes = totalSeconds / 60L
        val minutes = (totalMinutes % 60L).toInt()
        val hours = totalMinutes / 60L

        return "%02d:%02d:%02d:%03d".format(hours, minutes, seconds, milliseconds)
    }
}
