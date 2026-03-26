package ru.benos_codex.gofindwin.client.hud.effects

data class FinishEffectsConfig(
    val newRecord: String = "new_record",
    val average: String = "average",
    val worse: String = "worse"
) {
    fun profileId(category: FinishEffectCategory): String = when (category) {
        FinishEffectCategory.NEW_RECORD -> newRecord
        FinishEffectCategory.AVERAGE -> average
        FinishEffectCategory.WORSE -> worse
    }
}
